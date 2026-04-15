#!/usr/bin/env node

const fs = require('node:fs');
const path = require('node:path');

const ROOT_DIR = path.resolve(__dirname, '..');
const DEFAULT_ENV_FILE = path.join(ROOT_DIR, 'directus', '.env');

async function main() {
  const options = parseArgs(process.argv.slice(2));
  const config = loadConfig(options);
  await waitForDirectus(config.baseUrl);
  const authHeader = await getAuthHeader(config);

  const context = {
    baseUrl: config.baseUrl,
    publicUrl: config.publicUrl,
    authHeader,
    issues: [],
  };

  const [siteSettings, pages, pageSections, pageSectionItems, navigationItems] = await Promise.all([
    getSingletonItem(context, 'site_settings', 'fields=default_og_image,status'),
    listItems(context, 'page', [
      'limit=-1',
      'filter[status][_eq]=published',
      'fields=id,slug,path,title',
    ]),
    listItems(context, 'page_sections', [
      'limit=-1',
      'filter[status][_eq]=published',
      'fields=id,page,internal_name,section_type,title,image,image_alt,mobile_image,mobile_image_alt,primary_cta_label,primary_cta_url,secondary_cta_label,secondary_cta_url',
    ]),
    listItems(context, 'page_section_items', [
      'limit=-1',
      'filter[status][_eq]=published',
      'fields=id,page_section,title,label,url,image,image_alt',
    ]),
    listItems(context, 'navigation_items', [
      'limit=-1',
      'filter[status][_eq]=published',
      'fields=id,label,url,item_type,page,navigation',
    ]),
  ]);

  const pagesById = new Map(pages.map((page) => [page.id, page]));
  const sectionsById = new Map(pageSections.map((section) => [section.id, section]));
  const fileIds = collectFileIds(siteSettings, pageSections, pageSectionItems);
  const files = fileIds.length === 0
    ? []
    : await listFiles(context, fileIds);
  const filesById = new Map(files.map((file) => [file.id, file]));

  validateSiteSettings(context, siteSettings, filesById);
  validateSections(context, pageSections, pagesById, filesById);
  validateSectionItems(context, pageSectionItems, sectionsById, pagesById, filesById);
  validateNavigationItems(context, navigationItems, pagesById);

  if (options.json) {
    console.log(JSON.stringify({
      baseUrl: config.baseUrl,
      publicUrl: config.publicUrl,
      canonicalAssetBaseUrl: config.publicUrl || config.baseUrl,
      issueCount: context.issues.length,
      issues: context.issues,
    }, null, 2));
  } else if (context.issues.length === 0) {
    console.log(`Directus content audit passed. Canonical asset base URL: ${normalizeUrl(config.publicUrl || config.baseUrl)}/assets/{id}`);
  } else {
    console.log(`Directus content audit found ${context.issues.length} issue(s):`);
    for (const issue of context.issues) {
      console.log(`- [${issue.code}] ${issue.location}: ${issue.message}`);
    }
  }

  if (context.issues.length > 0) {
    process.exitCode = 1;
  }
}

function parseArgs(argv) {
  const options = {
    envFile: process.env.DIRECTUS_ENV_FILE || DEFAULT_ENV_FILE,
    json: false,
  };

  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];

    if (arg === '--env-file') {
      index += 1;
      options.envFile = requireValue(argv[index], '--env-file');
      continue;
    }

    if (arg === '--json') {
      options.json = true;
      continue;
    }

    if (arg === '--help' || arg === '-h') {
      printHelp();
      process.exit(0);
    }

    throw new Error(`Unsupported argument "${arg}".`);
  }

  options.envFile = path.resolve(options.envFile);
  return options;
}

function printHelp() {
  console.log(`Usage:
  node scripts/directus-content-audit.js [--env-file <path>] [--json]`);
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
  const baseUrl = normalizeUrl(env.DIRECTUS_BASE_URL || env.DIRECTUS_PUBLIC_URL || env.PUBLIC_URL || 'http://localhost:8055');
  const publicUrl = normalizeUrl(env.DIRECTUS_PUBLIC_URL || env.DIRECTUS_BASE_URL || env.PUBLIC_URL || 'http://localhost:8055');

  return {
    baseUrl,
    publicUrl,
    schemaAdminToken: env.DIRECTUS_SCHEMA_ADMIN_TOKEN || env.DIRECTUS_ADMIN_TOKEN || env.ADMIN_TOKEN || env.DIRECTUS_STATIC_TOKEN || '',
    adminEmail: env.DIRECTUS_ADMIN_EMAIL || env.ADMIN_EMAIL || '',
    adminPassword: env.DIRECTUS_ADMIN_PASSWORD || env.ADMIN_PASSWORD || '',
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

function normalizeUrl(value) {
  return String(value || '').replace(/\/+$/, '');
}

async function waitForDirectus(baseUrl) {
  for (let attempt = 0; attempt < 60; attempt += 1) {
    try {
      const response = await fetch(`${baseUrl}/server/health`);
      if (response.ok) {
        return;
      }
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
      'Directus content audit requires DIRECTUS_SCHEMA_ADMIN_TOKEN, DIRECTUS_STATIC_TOKEN, or DIRECTUS_ADMIN_EMAIL/DIRECTUS_ADMIN_PASSWORD.'
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

async function getSingletonItem(context, collection, query) {
  return requestJson(context.baseUrl, context.authHeader, 'GET', `/items/${collection}?${query}`);
}

async function listItems(context, collection, queryParts) {
  const payload = await requestJson(
    context.baseUrl,
    context.authHeader,
    'GET',
    `/items/${collection}?${queryParts.join('&')}`
  );

  return Array.isArray(payload) ? payload : [];
}

async function listFiles(context, fileIds) {
  const payload = await requestJson(
    context.baseUrl,
    context.authHeader,
    'GET',
    `/files?limit=-1&filter[id][_in]=${encodeURIComponent(fileIds.join(','))}&fields=id,title,description`
  );

  return Array.isArray(payload) ? payload : [];
}

function collectFileIds(siteSettings, pageSections, pageSectionItems) {
  const ids = new Set();

  addFileId(ids, siteSettings?.default_og_image);
  for (const section of pageSections) {
    addFileId(ids, section.image);
    addFileId(ids, section.mobile_image);
  }
  for (const item of pageSectionItems) {
    addFileId(ids, item.image);
  }

  return [...ids];
}

function addFileId(set, value) {
  if (typeof value === 'string' && value.trim()) {
    set.add(value.trim());
  }
}

function validateSiteSettings(context, siteSettings, filesById) {
  if (!siteSettings || siteSettings.status !== 'published' || !siteSettings.default_og_image) {
    return;
  }

  validateMediaField(context, {
    location: 'site_settings',
    fileId: siteSettings.default_og_image,
    altOverride: '',
    filesById,
    fieldName: 'default_og_image',
  });
}

function validateSections(context, sections, pagesById, filesById) {
  for (const section of sections) {
    const page = pagesById.get(section.page);
    const pageLabel = page ? `${page.slug} (${page.path})` : `page#${section.page}`;
    const sectionLabel = `${pageLabel} -> section "${section.internal_name || section.title || section.id}"`;

    validateMediaField(context, {
      location: sectionLabel,
      fileId: section.image,
      altOverride: section.image_alt,
      filesById,
      fieldName: 'image',
    });
    validateMediaField(context, {
      location: sectionLabel,
      fileId: section.mobile_image,
      altOverride: section.mobile_image_alt,
      filesById,
      fieldName: 'mobile_image',
    });

    validatePairedFields(context, sectionLabel, 'primary_cta', section.primary_cta_label, section.primary_cta_url);
    validatePairedFields(context, sectionLabel, 'secondary_cta', section.secondary_cta_label, section.secondary_cta_url);
  }
}

function validateSectionItems(context, items, sectionsById, pagesById, filesById) {
  for (const item of items) {
    const section = sectionsById.get(item.page_section);
    const page = section ? pagesById.get(section.page) : null;
    const location = `${page ? page.slug : 'page?'} -> section "${section?.internal_name || section?.title || item.page_section}" -> item "${item.title || item.id}"`;

    validateMediaField(context, {
      location,
      fileId: item.image,
      altOverride: item.image_alt,
      filesById,
      fieldName: 'image',
    });
  }
}

function validateNavigationItems(context, items, pagesById) {
  for (const item of items) {
    const itemType = String(item.item_type || '').trim();
    const location = `navigation_item "${item.label || item.id}"`;
    const hasUrl = hasText(item.url);
    const hasPage = item.page !== null && item.page !== undefined && item.page !== '';

    if ((itemType === 'internal_path' || itemType === 'external_url' || itemType === 'anchor') && !hasUrl) {
      pushIssue(context, 'missing_url', location, `${itemType} items must define url.`);
    }

    if (itemType === 'internal_page' && !hasPage) {
      pushIssue(context, 'missing_page_reference', location, 'internal_page items must define page.');
    }

    if (hasPage && !pagesById.has(item.page)) {
      pushIssue(context, 'broken_page_reference', location, `references missing page id ${item.page}.`);
    }
  }
}

function validateMediaField(context, { location, fileId, altOverride, filesById, fieldName }) {
  if (!hasText(fileId)) {
    return;
  }

  const normalizedFileId = fileId.trim();
  const file = filesById.get(normalizedFileId);
  if (!file) {
    pushIssue(context, 'missing_asset', location, `${fieldName} references missing file "${normalizedFileId}".`);
    return;
  }

  const fallbackAlt = firstText(altOverride, file.title, file.description);
  if (!fallbackAlt) {
    pushIssue(
      context,
      'missing_alt',
      location,
      `${fieldName}="${normalizedFileId}" has no alt override and no file title/description fallback.`
    );
  }
}

function validatePairedFields(context, location, prefix, labelValue, urlValue) {
  const hasLabel = hasText(labelValue);
  const hasUrl = hasText(urlValue);

  if (hasLabel !== hasUrl) {
    pushIssue(
      context,
      'cta_pair_mismatch',
      location,
      `${prefix}_label and ${prefix}_url must be set together.`
    );
  }
}

function pushIssue(context, code, location, message) {
  context.issues.push({ code, location, message });
}

function firstText(...values) {
  for (const value of values) {
    if (hasText(value)) {
      return value.trim();
    }
  }

  return '';
}

function hasText(value) {
  return typeof value === 'string' && value.trim().length > 0;
}

async function requestJson(baseUrl, authHeader, method, apiPath) {
  const response = await fetch(`${baseUrl}${apiPath}`, {
    method,
    headers: {
      Accept: 'application/json',
      Authorization: authHeader,
    },
  });

  if (response.status === 204) {
    return null;
  }

  const payload = await parseJsonResponse(response);

  if (!response.ok) {
    const message =
      payload?.errors?.[0]?.message ||
      payload?.error ||
      `${response.status} ${response.statusText}`.trim();
    throw new Error(`Directus API ${response.status} ${response.url}: ${message}`);
  }

  return payload?.data ?? payload ?? null;
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

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

main().catch((error) => {
  console.error(error.message || error);
  process.exit(1);
});
