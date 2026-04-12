#!/usr/bin/env node

const fs = require('node:fs');
const path = require('node:path');
const {
  seedPrefix,
  siteSettings,
  navigation,
  pages,
  faq,
  legalDocuments,
} = require('../directus/seed/initial-content');

const ROOT_DIR = path.resolve(__dirname, '..');
const DEFAULT_ENV_FILE = path.join(ROOT_DIR, 'directus', '.env');
const DEFAULT_LEGAL_DIR = path.join(ROOT_DIR, 'directus', 'seed', 'legal');

async function main() {
  const { options } = parseArgs(process.argv.slice(2));
  const config = loadConfig(options);
  await waitForDirectus(config.baseUrl);
  const authHeader = await getAuthHeader(config);

  const state = {
    baseUrl: config.baseUrl,
    authHeader,
    dryRun: options.dryRun,
    prune: options.prune,
    legalDir: config.legalDir,
    caches: {},
    summary: {
      site_settings: { created: 0, updated: 0, deleted: 0 },
      navigation: { created: 0, updated: 0, deleted: 0 },
      navigation_items: { created: 0, updated: 0, deleted: 0 },
      page: { created: 0, updated: 0, deleted: 0 },
      page_sections: { created: 0, updated: 0, deleted: 0 },
      page_section_items: { created: 0, updated: 0, deleted: 0 },
      faq: { created: 0, updated: 0, deleted: 0 },
      legal_documents: { created: 0, updated: 0, deleted: 0 },
    },
  };

  const legalBodiesByKey = loadRenderedLegalBodies(config.legalDir, siteSettings);

  await upsertSiteSettings(state);
  const navigationMap = await upsertNavigation(state);
  await upsertPages(state);
  await upsertFaq(state);
  await upsertLegalDocuments(state, legalBodiesByKey);

  if (state.prune) {
    await pruneSeededRecords(state, navigationMap);
  }

  printSummary(state.summary, state.dryRun);
}

function parseArgs(argv) {
  const options = {
    envFile: process.env.DIRECTUS_ENV_FILE || DEFAULT_ENV_FILE,
    legalDir: process.env.DIRECTUS_CONTENT_LEGAL_DIR || DEFAULT_LEGAL_DIR,
    dryRun: false,
    prune: false,
  };

  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];

    if (arg === '--env-file') {
      index += 1;
      options.envFile = requireValue(argv[index], '--env-file');
      continue;
    }

    if (arg === '--legal-dir') {
      index += 1;
      options.legalDir = requireValue(argv[index], '--legal-dir');
      continue;
    }

    if (arg === '--dry-run') {
      options.dryRun = true;
      continue;
    }

    if (arg === '--prune') {
      options.prune = true;
      continue;
    }

    throw new Error(`Unsupported argument "${arg}".`);
  }

  options.envFile = path.resolve(options.envFile);
  options.legalDir = path.resolve(options.legalDir);
  return { options };
}

function requireValue(value, flag) {
  if (!value) {
    throw new Error(`Missing value for ${flag}.`);
  }

  return value;
}

function loadConfig(options) {
  const fileEnv = loadEnvFile(options.envFile);
  const env = { ...fileEnv, ...process.env };
  const baseUrl = normalizeBaseUrl(env.DIRECTUS_BASE_URL || env.DIRECTUS_PUBLIC_URL || 'http://localhost:8055');

  return {
    baseUrl,
    legalDir: options.legalDir,
    schemaAdminToken: env.DIRECTUS_SCHEMA_ADMIN_TOKEN || env.DIRECTUS_ADMIN_TOKEN || '',
    adminEmail: env.DIRECTUS_ADMIN_EMAIL || '',
    adminPassword: env.DIRECTUS_ADMIN_PASSWORD || '',
  };
}

function loadEnvFile(envFile) {
  if (!envFile || !fs.existsSync(envFile)) {
    return {};
  }

  const parsed = {};
  for (const rawLine of fs.readFileSync(envFile, 'utf8').split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || line.startsWith('#')) continue;
    const separatorIndex = line.indexOf('=');
    if (separatorIndex === -1) continue;

    const key = line.slice(0, separatorIndex).trim();
    let value = line.slice(separatorIndex + 1).trim();
    if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
      value = value.slice(1, -1);
    }
    parsed[key] = value;
  }

  return parsed;
}

function normalizeBaseUrl(value) {
  return value.replace(/\/+$/, '');
}

async function waitForDirectus(baseUrl) {
  for (let attempt = 0; attempt < 60; attempt += 1) {
    try {
      const response = await fetch(`${baseUrl}/server/health`);
      if (response.ok) return;
    } catch (error) {
      // Keep waiting.
    }

    await sleep(2000);
  }

  throw new Error(`Timed out waiting for Directus at ${baseUrl}/server/health`);
}

async function getAuthHeader(config) {
  if (config.schemaAdminToken) {
    return `Bearer ${config.schemaAdminToken}`;
  }

  if (!config.adminEmail || !config.adminPassword) {
    throw new Error(
      'Directus content import requires DIRECTUS_SCHEMA_ADMIN_TOKEN or DIRECTUS_ADMIN_EMAIL/DIRECTUS_ADMIN_PASSWORD.'
    );
  }

  const response = await fetch(`${config.baseUrl}/auth/login`, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      email: config.adminEmail,
      password: config.adminPassword,
    }),
  });

  const payload = await parseJsonResponse(response);
  const accessToken = payload?.data?.access_token;

  if (!response.ok || !accessToken) {
    throw new Error(`Directus login failed at ${config.baseUrl}/auth/login`);
  }

  return `Bearer ${accessToken}`;
}

function loadRenderedLegalBodies(legalDir, settings) {
  const tokens = {
    LEGAL_ENTITY_SHORT: settings.legal_entity_short,
    LEGAL_ENTITY_LONG: settings.legal_entity_full,
    LEGAL_INN: settings.legal_inn,
    LEGAL_OGRNIP: settings.legal_ogrnip,
    LEGAL_PHONE: settings.support_phone,
    LEGAL_EMAIL: settings.support_email,
    LEGAL_ADDRESS: settings.legal_address,
    SITE_NAME: settings.site_name,
    SITE_URL: 'https://yug-postel.ru',
    SITE_HOST: 'yug-postel.ru',
    PUBLIC_URL: '',
  };

  const bodies = {};

  for (const document of legalDocuments) {
    const templatePath = path.join(legalDir, document.file_name);
    const template = fs.readFileSync(templatePath, 'utf8');
    bodies[document.document_key] = applyTokens(template, tokens);
  }

  return bodies;
}

function applyTokens(html, tokens) {
  return Object.entries(tokens).reduce((acc, [key, value]) => acc.split(`{{${key}}}`).join(value ?? ''), html);
}

async function upsertSiteSettings(state) {
  const existing = await getSingletonItem(state, 'site_settings');
  const payload = withStatus(siteSettings, existing);

  await upsertSingletonItem(state, 'site_settings', payload, Boolean(existing));
}

async function upsertNavigation(state) {
  const groupMap = new Map();

  for (const group of navigation) {
    const existing = await findItem(state, 'navigation', (item) => item.key === group.key);
    const payload = withStatus(
      {
        key: group.key,
        title: group.title,
        placement: group.placement,
        description: group.description,
        sort: group.sort,
      },
      existing
    );

    const record = existing
      ? await updateItem(state, 'navigation', existing.id, payload)
      : await createItem(state, 'navigation', payload);

    groupMap.set(group.key, record);

    for (const item of group.items) {
      const existingItem = await findItem(
        state,
        'navigation_items',
        (candidate) => candidate.migration_key === item.migration_key
      );

      const navigationItemPayload = withStatus(
        {
          migration_key: item.migration_key,
          navigation: record.id,
          label: item.label,
          url: item.url,
          item_type: 'internal_path',
          open_in_new_tab: false,
          visibility: 'all',
          sort: item.sort,
        },
        existingItem
      );

      if (existingItem) {
        await updateItem(state, 'navigation_items', existingItem.id, navigationItemPayload);
      } else {
        await createItem(state, 'navigation_items', navigationItemPayload);
      }
    }
  }

  return groupMap;
}

async function upsertPages(state) {
  for (const page of pages) {
    const existing = await findItem(state, 'page', (item) => item.path === page.path);
    const payload = withStatus(
      {
        title: page.title,
        slug: page.slug,
        path: page.path,
        template: page.template,
        nav_label: page.nav_label,
        summary: page.summary,
        seo_title: page.seo_title,
        seo_description: page.seo_description,
      },
      existing
    );

    const record = existing
      ? await updateItem(state, 'page', existing.id, payload)
      : await createItem(state, 'page', payload);

    for (const section of page.sections || []) {
      const existingSection = await findItem(
        state,
        'page_sections',
        (candidate) => candidate.migration_key === section.migration_key
      );

      const sectionPayload = withStatus(
        {
          migration_key: section.migration_key,
          page: record.id,
          internal_name: section.internal_name,
          section_type: section.section_type,
          sort: section.sort,
          anchor_id: section.anchor_id || null,
          eyebrow: section.eyebrow || null,
          title: section.title || null,
          accent: section.accent || null,
          body: section.body || null,
          primary_cta_label: section.primary_cta_label || null,
          primary_cta_url: section.primary_cta_url || null,
          secondary_cta_label: section.secondary_cta_label || null,
          secondary_cta_url: section.secondary_cta_url || null,
          style_variant: section.style_variant || 'default',
          layout_variant: section.layout_variant || 'contained',
        },
        existingSection
      );

      const sectionRecord = existingSection
        ? await updateItem(state, 'page_sections', existingSection.id, sectionPayload)
        : await createItem(state, 'page_sections', sectionPayload);

      for (const item of section.items || []) {
        const existingItem = await findItem(
          state,
          'page_section_items',
          (candidate) => candidate.migration_key === item.migration_key
        );

        const itemPayload = withStatus(
          {
            migration_key: item.migration_key,
            page_section: sectionRecord.id,
            title: item.title,
            description: item.description || null,
            label: item.label || null,
            url: item.url || null,
            reference_kind: item.reference_kind || 'none',
            reference_key: item.reference_key || null,
            sort: item.sort,
          },
          existingItem
        );

        if (existingItem) {
          await updateItem(state, 'page_section_items', existingItem.id, itemPayload);
        } else {
          await createItem(state, 'page_section_items', itemPayload);
        }
      }
    }
  }
}

async function upsertFaq(state) {
  for (const entry of faq) {
    const existing = await findItem(state, 'faq', (item) => item.migration_key === entry.migration_key);
    const payload = withStatus(
      {
        migration_key: entry.migration_key,
        question: entry.question,
        answer: entry.answer,
        category: entry.category,
        is_featured: entry.is_featured,
        sort: entry.sort,
      },
      existing
    );

    if (existing) {
      await updateItem(state, 'faq', existing.id, payload);
    } else {
      await createItem(state, 'faq', payload);
    }
  }
}

async function upsertLegalDocuments(state, legalBodiesByKey) {
  for (const entry of legalDocuments) {
    const existing = await findItem(state, 'legal_documents', (item) => item.document_key === entry.document_key);
    const payload = withStatus(
      {
        document_key: entry.document_key,
        title: entry.title,
        slug: entry.slug,
        path: entry.path,
        summary: entry.summary,
        body_html: legalBodiesByKey[entry.document_key],
        sort: entry.sort,
      },
      existing
    );

    if (existing) {
      await updateItem(state, 'legal_documents', existing.id, payload);
    } else {
      await createItem(state, 'legal_documents', payload);
    }
  }
}

async function pruneSeededRecords(state) {
  const keepers = {
    navigation_items: new Set(navigation.flatMap((group) => group.items.map((item) => item.migration_key))),
    page_sections: new Set(pages.flatMap((page) => (page.sections || []).map((section) => section.migration_key))),
    page_section_items: new Set(
      pages.flatMap((page) =>
        (page.sections || []).flatMap((section) => (section.items || []).map((item) => item.migration_key))
      )
    ),
    faq: new Set(faq.map((entry) => entry.migration_key)),
  };

  for (const [collection, keepSet] of Object.entries(keepers)) {
    const items = await listItems(state, collection);
    const seededItems = items.filter((item) => typeof item.migration_key === 'string' && item.migration_key.startsWith(seedPrefix));
    for (const item of seededItems) {
      if (!keepSet.has(item.migration_key)) {
        await deleteItem(state, collection, item.id);
      }
    }
  }
}

function withStatus(payload, existing) {
  return {
    ...payload,
    status: 'published',
    published_at: existing?.published_at || new Date().toISOString(),
  };
}

async function findItem(state, collection, predicate) {
  const items = await listItems(state, collection);
  return items.find(predicate) || null;
}

async function listItems(state, collection) {
  if (!state.caches[collection]) {
    const items = await requestJson(state.baseUrl, state.authHeader, 'GET', `/items/${collection}?limit=-1`);
    state.caches[collection] = Array.isArray(items) ? items : [];
  }

  return state.caches[collection];
}

async function getSingletonItem(state, collection) {
  const item = await requestJson(state.baseUrl, state.authHeader, 'GET', `/items/${collection}`);
  return item && typeof item === 'object' && !Array.isArray(item) ? item : null;
}

async function upsertSingletonItem(state, collection, payload, hasExisting) {
  if (hasExisting) {
    state.summary[collection].updated += 1;
  } else {
    state.summary[collection].created += 1;
  }

  if (state.dryRun) {
    return payload;
  }

  return requestJson(state.baseUrl, state.authHeader, 'PATCH', `/items/${collection}`, payload);
}

async function createItem(state, collection, payload) {
  state.summary[collection].created += 1;
  if (state.dryRun) {
    return { id: `dry-run:${collection}:${state.summary[collection].created}`, ...payload };
  }

  const created = await requestJson(state.baseUrl, state.authHeader, 'POST', `/items/${collection}`, payload);
  state.caches[collection] = [...(state.caches[collection] || []), created];
  return created;
}

async function updateItem(state, collection, id, payload) {
  state.summary[collection].updated += 1;
  if (state.dryRun) {
    return { id, ...payload };
  }

  const updated = await requestJson(state.baseUrl, state.authHeader, 'PATCH', `/items/${collection}/${id}`, payload);
  state.caches[collection] = (state.caches[collection] || []).map((item) => (item.id === id ? updated : item));
  return updated;
}

async function deleteItem(state, collection, id) {
  state.summary[collection].deleted += 1;
  if (state.dryRun) {
    return;
  }

  await requestNoContent(state.baseUrl, state.authHeader, 'DELETE', `/items/${collection}/${id}`);
  state.caches[collection] = (state.caches[collection] || []).filter((item) => item.id !== id);
}

function printSummary(summary, dryRun) {
  console.log(dryRun ? 'Dry run summary:' : 'Content import summary:');
  for (const [collection, stats] of Object.entries(summary)) {
    console.log(
      `- ${collection}: created=${stats.created} updated=${stats.updated} deleted=${stats.deleted}`
    );
  }
}

async function requestJson(baseUrl, authHeader, method, apiPath, body) {
  const response = await fetch(`${baseUrl}${apiPath}`, {
    method,
    headers: buildHeaders(authHeader, body !== undefined),
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  if (response.status === 204) {
    return null;
  }

  const payload = await parseJsonResponse(response);
  if (!response.ok) {
    throw buildRequestError(response, payload);
  }

  return payload?.data ?? payload ?? null;
}

async function requestNoContent(baseUrl, authHeader, method, apiPath, body) {
  const response = await fetch(`${baseUrl}${apiPath}`, {
    method,
    headers: buildHeaders(authHeader, body !== undefined),
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  if (response.status === 204) {
    return;
  }

  const payload = await parseJsonResponse(response);
  if (!response.ok) {
    throw buildRequestError(response, payload);
  }
}

function buildHeaders(authHeader, hasBody) {
  const headers = {
    Accept: 'application/json',
    Authorization: authHeader,
  };

  if (hasBody) {
    headers['Content-Type'] = 'application/json';
  }

  return headers;
}

async function parseJsonResponse(response) {
  const text = await response.text();
  if (!text.trim()) {
    return null;
  }

  try {
    return JSON.parse(text);
  } catch (error) {
    throw new Error(`Failed to parse JSON response from ${response.url}`);
  }
}

function buildRequestError(response, payload) {
  const message =
    payload?.errors?.[0]?.message ||
    payload?.error ||
    `${response.status} ${response.statusText}`.trim();

  return new Error(`Directus API ${response.status} ${response.url}: ${message}`);
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

main().catch((error) => {
  console.error(error.message || error);
  process.exit(1);
});
