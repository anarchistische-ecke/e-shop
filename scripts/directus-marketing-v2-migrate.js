#!/usr/bin/env node

const fs = require('node:fs');
const path = require('node:path');

const ROOT_DIR = path.resolve(__dirname, '..');
const DEFAULT_ENV_FILE = path.join(ROOT_DIR, 'directus', '.env');
const DEFAULT_STOREFRONT_ROOT = path.resolve(ROOT_DIR, '..', 'cozyhome');
const HOME_SLOT_MIGRATION_KEY = 'marketing-v2:home:campaign-slot';
const LEGAL_DOCUMENT_SOURCES = [
  {
    key: 'privacy-policy',
    slug: 'privacy-policy',
    fileName: 'privacy.html',
    path: '/konfidentsialnost-i-zashchita-informatsii',
    title: 'Политика обработки персональных данных',
    summary: 'Правила обработки и защиты персональных данных пользователей и покупателей.',
  },
  {
    key: 'user-agreement',
    slug: 'user-agreement',
    fileName: 'user-agreement.html',
    path: '/polzovatelskoe-soglashenie',
    title: 'Пользовательское соглашение',
    summary: 'Условия использования сайта и ответственность сторон.',
  },
  {
    key: 'personal-data-consent',
    slug: 'personal-data-consent',
    fileName: 'pd-consent.html',
    path: '/soglasie-na-obrabotku-pd',
    title: 'Согласие на обработку персональных данных',
    summary: 'Форма согласия на обработку данных в рамках работы сайта.',
  },
  {
    key: 'ads-consent',
    slug: 'ads-consent',
    fileName: 'ads-consent.html',
    path: '/soglasie-na-poluchenie-reklamy',
    title: 'Согласие на получение рекламы',
    summary: 'Порядок подписки и отказа от рекламных сообщений.',
  },
  {
    key: 'cookies-policy',
    slug: 'cookies-policy',
    fileName: 'cookies.html',
    path: '/kuki',
    title: 'Политика в отношении куки',
    summary: 'Информация об использовании куки и иных технологий аналитики.',
  },
  {
    key: 'sales-terms',
    slug: 'sales-terms',
    fileName: 'sales-terms.html',
    path: '/usloviya-prodazhi',
    title: 'Условия продажи (публичная оферта)',
    summary: 'Правила оформления заказов, оплаты, доставки и возврата товара.',
  },
];

function parseArgs(argv) {
  const options = {
    dryRun: false,
    assertIdempotent: false,
    envFile: process.env.DIRECTUS_ENV_FILE || DEFAULT_ENV_FILE,
    storefrontRoot: process.env.STOREFRONT_ROOT || DEFAULT_STOREFRONT_ROOT,
  };
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === '--dry-run') {
      options.dryRun = true;
    } else if (arg === '--assert-idempotent') {
      options.assertIdempotent = true;
      options.dryRun = true;
    } else if (arg === '--env-file') {
      options.envFile = path.resolve(requireValue(argv[++index], '--env-file'));
    } else if (arg === '--storefront-root') {
      options.storefrontRoot = path.resolve(requireValue(argv[++index], '--storefront-root'));
    } else if (arg === '--help' || arg === '-h') {
      options.help = true;
    } else {
      throw new Error(`Unsupported argument "${arg}".`);
    }
  }
  return options;
}

function requireValue(value, flag) {
  if (!value) throw new Error(`Missing value for ${flag}.`);
  return value;
}

function loadEnvFile(envFile) {
  if (!envFile || !fs.existsSync(envFile)) return {};
  const result = {};
  fs.readFileSync(envFile, 'utf8').split(/\r?\n/).forEach((rawLine) => {
    const line = rawLine.trim();
    if (!line || line.startsWith('#')) return;
    const separator = line.indexOf('=');
    if (separator < 0) return;
    const key = line.slice(0, separator).trim();
    let value = line.slice(separator + 1).trim();
    if (
      (value.startsWith('"') && value.endsWith('"'))
      || (value.startsWith("'") && value.endsWith("'"))
    ) {
      value = value.slice(1, -1);
    }
    result[key] = value;
  });
  return result;
}

function normalizeBaseUrl(value) {
  return String(value || '').replace(/\/+$/, '');
}

function normalizeKind(value) {
  return String(value || '').trim().toLowerCase().replace(/[-\s]+/g, '_');
}

function hasValue(value) {
  return value !== undefined && value !== null && String(value).trim() !== '';
}

function copyTypedReference(item, collectionIdByKey = new Map()) {
  const kind = normalizeKind(item.reference_kind);
  const key = String(item.reference_key || '').trim();
  if (!key) return {};
  if (['product', 'product_slug', 'product_id'].includes(kind) && !hasValue(item.product_key)) {
    return { product_key: key };
  }
  if (['category', 'category_slug', 'category_id'].includes(kind) && !hasValue(item.category_key)) {
    return { category_key: key };
  }
  if (['brand', 'brand_slug', 'brand_id'].includes(kind) && !hasValue(item.brand_key)) {
    return { brand_key: key };
  }
  if (
    ['collection', 'collection_key', 'cms_collection', 'storefront_collection'].includes(kind)
    && !hasValue(item.storefront_collection)
    && collectionIdByKey.has(key)
  ) {
    return { storefront_collection: collectionIdByKey.get(key) };
  }
  return {};
}

function copyCollectionItemReference(item) {
  const kind = normalizeKind(item.entity_kind);
  const key = String(item.entity_key || '').trim();
  if (!key) return {};
  if (kind === 'product' && !hasValue(item.product_key)) return { product_key: key };
  if (kind === 'category' && !hasValue(item.category_key)) return { category_key: key };
  return {};
}

function createSummary() {
  return {
    page_section_items: { scanned: 0, updated: 0 },
    storefront_collection_items: { scanned: 0, updated: 0 },
    page_sections: { scanned: 0, created: 0 },
    page_section_faqs: { created: 0 },
    page_section_legal_documents: { created: 0 },
    legal_documents: { scanned: 0, imported: 0 },
    site_settings: { announcementLinked: 0 },
    recordCounts: { before: {}, after: {} },
    validation: {
      unresolvedReferences: [],
      duplicateKeys: [],
      missingMedia: [],
      missingMediaReferences: [],
      invalidRoutes: [],
      danglingRelations: [],
      publishedWithoutTimestamp: [],
      ok: false,
    },
  };
}

function plannedMutationCount(summary) {
  return [
    summary.page_section_items.updated,
    summary.storefront_collection_items.updated,
    summary.page_sections.created,
    summary.page_section_faqs.created,
    summary.page_section_legal_documents.created,
    summary.legal_documents.imported,
    summary.site_settings.announcementLinked,
  ].reduce((total, value) => total + Number(value || 0), 0);
}

async function parseResponse(response) {
  const text = await response.text();
  let payload = null;
  if (text) {
    try {
      payload = JSON.parse(text);
    } catch {
      payload = { raw: text };
    }
  }
  if (!response.ok) {
    const message = payload?.errors?.[0]?.message || payload?.error || response.statusText;
    throw new Error(`Directus ${response.status}: ${message}`);
  }
  return payload;
}

async function login(config) {
  if (config.token) return `Bearer ${config.token}`;
  if (!config.email || !config.password) {
    throw new Error(
      'Set DIRECTUS_SCHEMA_ADMIN_TOKEN or DIRECTUS_ADMIN_EMAIL/DIRECTUS_ADMIN_PASSWORD.'
    );
  }
  const response = await fetch(`${config.baseUrl}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: config.email, password: config.password }),
  });
  const payload = await parseResponse(response);
  return `Bearer ${payload.data.access_token}`;
}

function createClient(config, authorization) {
  let dryRunSequence = 0;

  async function request(method, pathname, body) {
    const response = await fetch(`${config.baseUrl}${pathname}`, {
      method,
      headers: {
        Authorization: authorization,
        Accept: 'application/json',
        ...(body ? { 'Content-Type': 'application/json' } : {}),
      },
      ...(body ? { body: JSON.stringify(body) } : {}),
    });
    return parseResponse(response);
  }

  return {
    async list(collection, fields = '*') {
      const query = new URLSearchParams({ fields, limit: '-1' });
      const payload = await request('GET', `/items/${collection}?${query}`);
      if (Array.isArray(payload?.data)) return payload.data;
      return payload?.data ? [payload.data] : [];
    },
    async listFiles() {
      const query = new URLSearchParams({ fields: 'id', limit: '-1' });
      const payload = await request('GET', `/files?${query}`);
      return Array.isArray(payload?.data) ? payload.data : [];
    },
    async update(collection, id, payload) {
      if (config.dryRun) return { id, ...payload };
      return (await request('PATCH', `/items/${collection}/${encodeURIComponent(id)}`, payload))?.data;
    },
    async create(collection, payload) {
      if (config.dryRun) {
        dryRunSequence += 1;
        return { id: `dry-run:${collection}:${dryRunSequence}`, ...payload };
      }
      return (await request('POST', `/items/${collection}`, payload))?.data;
    },
  };
}

function applyTokens(html, tokens) {
  return Object.entries(tokens).reduce(
    (result, [key, value]) => result.split(`{{${key}}}`).join(value ?? ''),
    String(html || '')
  );
}

function legalTokens(env, siteSettings = {}) {
  const siteUrl = normalizeBaseUrl(
    env.STOREFRONT_PUBLIC_URL
      || env.REACT_APP_SITE_URL
      || env.SITE_URL
      || 'https://yug-postel.ru'
  );
  let siteHost = siteUrl;
  try {
    siteHost = new URL(siteUrl).host;
  } catch {
    // Validation below retains the configured value if it is not a URL.
  }
  return {
    LEGAL_ENTITY_SHORT:
      siteSettings.legal_entity_short || env.LEGAL_ENTITY_SHORT || '',
    LEGAL_ENTITY_LONG:
      siteSettings.legal_entity_full || env.LEGAL_ENTITY_LONG || '',
    LEGAL_INN: siteSettings.legal_inn || env.LEGAL_INN || '',
    LEGAL_OGRNIP: siteSettings.legal_ogrnip || env.LEGAL_OGRNIP || '',
    LEGAL_PHONE: siteSettings.support_phone || env.LEGAL_PHONE || '',
    LEGAL_EMAIL: siteSettings.support_email || env.LEGAL_EMAIL || '',
    LEGAL_ADDRESS: siteSettings.legal_address || env.LEGAL_ADDRESS || '',
    SITE_NAME: siteSettings.site_name || env.SITE_NAME || '',
    SITE_URL: siteUrl,
    SITE_HOST: siteHost,
    PUBLIC_URL: '',
  };
}

async function importStaticLegalDocuments(
  client,
  summary,
  collections,
  storefrontRoot,
  env
) {
  summary.legal_documents.scanned = collections.legal_documents.length;
  const existingKeys = new Set(
    collections.legal_documents.flatMap((document) => [
      String(document.document_key || '').trim(),
      String(document.slug || '').trim(),
    ]).filter(Boolean)
  );
  const sourceDir = path.join(storefrontRoot, 'public', 'legal');
  const tokens = legalTokens(env, collections.site_settings[0] || {});
  const missingDefinitions = LEGAL_DOCUMENT_SOURCES.filter(
    (definition) => !existingKeys.has(definition.key) && !existingKeys.has(definition.slug)
  );
  if (missingDefinitions.length > 0) {
    const requiredTokens = [
      'LEGAL_ENTITY_SHORT',
      'LEGAL_ENTITY_LONG',
      'LEGAL_INN',
      'LEGAL_OGRNIP',
      'LEGAL_PHONE',
      'LEGAL_EMAIL',
      'LEGAL_ADDRESS',
      'SITE_NAME',
    ];
    const missingTokens = requiredTokens.filter((name) => !hasValue(tokens[name]));
    if (missingTokens.length > 0) {
      throw new Error(
        `Cannot import commercial legal documents: set ${missingTokens.join(', ')} `
          + 'in site_settings or the migration environment.'
      );
    }
  }

  for (const [index, definition] of LEGAL_DOCUMENT_SOURCES.entries()) {
    if (existingKeys.has(definition.key) || existingKeys.has(definition.slug)) continue;
    const sourcePath = path.join(sourceDir, definition.fileName);
    if (!fs.existsSync(sourcePath)) {
      throw new Error(`Static legal source is missing: ${sourcePath}`);
    }
    const bodyHtml = applyTokens(fs.readFileSync(sourcePath, 'utf8'), tokens);
    const imported = await client.create('legal_documents', {
      status: 'published',
      published_at: new Date().toISOString(),
      document_key: definition.key,
      slug: definition.slug,
      path: definition.path,
      title: definition.title,
      summary: definition.summary,
      body_html: bodyHtml,
      version_label: 'Импортировано из текущей витрины',
      change_note: 'Первичный импорт Marketing V2; статический файл сохранен как rollback.',
      sort: index + 1,
    });
    collections.legal_documents.push(imported);
    existingKeys.add(definition.key);
    existingKeys.add(definition.slug);
    summary.legal_documents.imported += 1;
  }
}

async function ensureSiteSettingsAnnouncement(client, summary, collections) {
  const settings = collections.site_settings[0];
  if (!settings || hasValue(settings.announcement_banner)) return;
  const candidates = collections.banner.filter((banner) => {
    const placement = normalizeKind(banner.placement || banner.banner_type);
    return placement === 'sitewide_announcement' && banner.status === 'published';
  });
  if (candidates.length !== 1) return;
  await client.update('site_settings', settings.id, {
    announcement_banner: candidates[0].id,
  });
  settings.announcement_banner = candidates[0].id;
  summary.site_settings.announcementLinked += 1;
}

function duplicates(items, field) {
  const seen = new Set();
  const duplicateValues = new Set();
  items.forEach((item) => {
    const value = String(item?.[field] || '').trim();
    if (!value) return;
    if (seen.has(value)) duplicateValues.add(value);
    seen.add(value);
  });
  return [...duplicateValues];
}

async function migrateTypedReferences(client, summary, collections) {
  const storefrontCollections = collections.storefront_collection;
  const collectionIdByKey = new Map(
    storefrontCollections
      .filter((item) => hasValue(item.key))
      .map((item) => [String(item.key).trim(), item.id])
  );
  for (const item of collections.page_section_items) {
    summary.page_section_items.scanned += 1;
    const patch = copyTypedReference(item, collectionIdByKey);
    if (Object.keys(patch).length > 0) {
      await client.update('page_section_items', item.id, patch);
      Object.assign(item, patch);
      summary.page_section_items.updated += 1;
    }
    const kind = normalizeKind(item.reference_kind);
    if (
      hasValue(item.reference_key)
      && ['collection', 'collection_key', 'cms_collection', 'storefront_collection'].includes(kind)
      && !hasValue(item.storefront_collection)
    ) {
      summary.validation.unresolvedReferences.push({
        collection: 'page_section_items',
        id: item.id,
        kind,
        key: item.reference_key,
      });
    }
  }

  for (const item of collections.storefront_collection_item) {
    summary.storefront_collection_items.scanned += 1;
    const patch = copyCollectionItemReference(item);
    if (Object.keys(patch).length > 0) {
      await client.update('storefront_collection_item', item.id, patch);
      Object.assign(item, patch);
      summary.storefront_collection_items.updated += 1;
    }
  }
}

async function ensureHomeCampaignSlot(client, summary, collections) {
  const home = collections.page.find((item) => item.slug === 'home');
  if (!home) {
    throw new Error('Cannot add standard campaign slot: the home page is absent.');
  }
  const existing = collections.page_sections.find(
    (item) => item.migration_key === HOME_SLOT_MIGRATION_KEY
  );
  if (existing) return existing;

  const maxSort = collections.page_sections
    .filter((item) => String(item.page) === String(home.id))
    .reduce((maximum, item) => Math.max(maximum, Number(item.sort || 0)), 0);
  const created = await client.create('page_sections', {
    page: home.id,
    status: home.status === 'published' ? 'published' : 'draft',
    published_at: home.status === 'published' ? (home.published_at || new Date().toISOString()) : null,
    internal_name: 'Активные маркетинговые кампании',
    section_type: 'campaign_slot',
    campaign_placement: 'home_promo',
    item_limit: 2,
    style_variant: 'default',
    layout_variant: 'cards',
    sort: maxSort + 1,
    migration_key: HOME_SLOT_MIGRATION_KEY,
  });
  collections.page_sections.push(created);
  summary.page_sections.created += 1;
  return created;
}

async function ensureSectionRelations(client, summary, collections) {
  const faqSections = collections.page_sections.filter(
    (item) => normalizeKind(item.section_type) === 'faq'
  );
  const legalSections = collections.page_sections.filter(
    (item) => normalizeKind(item.section_type) === 'legal_document_list'
  );

  const faqRelationKeys = new Set(
    collections.page_section_faqs.map((item) => `${item.page_section}:${item.faq}`)
  );
  for (const section of faqSections) {
    for (const [index, faq] of collections.faq.entries()) {
      const key = `${section.id}:${faq.id}`;
      if (faqRelationKeys.has(key)) continue;
      const created = await client.create('page_section_faqs', {
        status: faq.status === 'published' && section.status === 'published' ? 'published' : 'draft',
        published_at: faq.published_at || section.published_at || null,
        page_section: section.id,
        faq: faq.id,
        sort: Number(faq.sort ?? index + 1),
      });
      collections.page_section_faqs.push(created);
      faqRelationKeys.add(key);
      summary.page_section_faqs.created += 1;
    }
  }

  const legalRelationKeys = new Set(
    collections.page_section_legal_documents.map(
      (item) => `${item.page_section}:${item.legal_document}`
    )
  );
  for (const section of legalSections) {
    for (const [index, document] of collections.legal_documents.entries()) {
      const key = `${section.id}:${document.id}`;
      if (legalRelationKeys.has(key)) continue;
      const created = await client.create('page_section_legal_documents', {
        status: document.status === 'published' && section.status === 'published'
          ? 'published'
          : 'draft',
        published_at: document.published_at || section.published_at || null,
        page_section: section.id,
        legal_document: document.id,
        sort: Number(document.sort ?? index + 1),
      });
      collections.page_section_legal_documents.push(created);
      legalRelationKeys.add(key);
      summary.page_section_legal_documents.created += 1;
    }
  }
}

function validateMedia(collections, summary) {
  const knownFileIds = new Set(
    (collections.directus_files || []).map((file) => String(file.id))
  );
  for (const [collectionName, items] of Object.entries({
    banner: collections.banner,
    page_sections: collections.page_sections,
    page_section_items: collections.page_section_items,
    page: collections.page,
    site_settings: collections.site_settings,
  })) {
    (items || []).forEach((item) => {
      if (hasValue(item.image) && !hasValue(item.image_alt)) {
        summary.validation.missingMedia.push({
          collection: collectionName,
          id: item.id,
          field: 'image_alt',
        });
      }
      if (hasValue(item.mobile_image) && !hasValue(item.mobile_image_alt)) {
        summary.validation.missingMedia.push({
          collection: collectionName,
          id: item.id,
          field: 'mobile_image_alt',
        });
      }
      for (const field of ['image', 'mobile_image', 'seo_image', 'default_og_image']) {
        if (
          hasValue(item[field])
          && !knownFileIds.has(String(item[field]))
        ) {
          summary.validation.missingMediaReferences.push({
            collection: collectionName,
            id: item.id,
            field,
            file: item[field],
          });
        }
      }
    });
  }
}

function validateContent(collections, summary) {
  const duplicateSpecs = [
    ['page', 'slug'],
    ['page', 'path'],
    ['legal_documents', 'document_key'],
    ['legal_documents', 'slug'],
    ['campaign', 'slug'],
    ['navigation', 'key'],
    ['storefront_collection', 'key'],
  ];
  duplicateSpecs.forEach(([collection, field]) => {
    summary.validation.duplicateKeys.push(
      ...duplicates(collections[collection] || [], field)
        .map((value) => `${collection}.${field}:${value}`)
    );
  });

  for (const collection of ['page', 'legal_documents']) {
    (collections[collection] || []).forEach((item) => {
      const route = String(item.path || '').trim();
      if (route && (!route.startsWith('/') || route.startsWith('//'))) {
        summary.validation.invalidRoutes.push({
          collection,
          id: item.id,
          path: route,
        });
      }
    });
  }

  const statusCollections = [
    'site_settings',
    'page',
    'page_sections',
    'page_section_items',
    'page_section_banners',
    'page_section_faqs',
    'page_section_legal_documents',
    'faq',
    'legal_documents',
    'banner',
    'campaign',
    'navigation',
    'navigation_items',
    'storefront_collection',
    'storefront_collection_item',
  ];
  statusCollections.forEach((collection) => {
    (collections[collection] || []).forEach((item) => {
      if (item.status === 'published' && !hasValue(item.published_at)) {
        summary.validation.publishedWithoutTimestamp.push({
          collection,
          id: item.id,
        });
      }
    });
  });

  const ids = (collection) => new Set(
    (collections[collection] || []).map((item) => String(item.id))
  );
  const relationSpecs = [
    ['page_sections', 'page', 'page'],
    ['page_section_items', 'page_section', 'page_sections'],
    ['page_section_banners', 'page_section', 'page_sections'],
    ['page_section_banners', 'banner', 'banner'],
    ['page_section_faqs', 'page_section', 'page_sections'],
    ['page_section_faqs', 'faq', 'faq'],
    ['page_section_legal_documents', 'page_section', 'page_sections'],
    ['page_section_legal_documents', 'legal_document', 'legal_documents'],
    ['navigation_items', 'navigation', 'navigation'],
    ['navigation_items', 'page', 'page'],
    ['banner', 'campaign', 'campaign'],
    ['storefront_collection_item', 'storefront_collection', 'storefront_collection'],
  ];
  relationSpecs.forEach(([collection, field, target]) => {
    const targetIds = ids(target);
    (collections[collection] || []).forEach((item) => {
      if (hasValue(item[field]) && !targetIds.has(String(item[field]))) {
        summary.validation.danglingRelations.push({
          collection,
          id: item.id,
          field,
          value: item[field],
          target,
        });
      }
    });
  });

  const failureKeys = [
    'unresolvedReferences',
    'duplicateKeys',
    'missingMedia',
    'missingMediaReferences',
    'invalidRoutes',
    'danglingRelations',
    'publishedWithoutTimestamp',
  ];
  summary.validation.ok = failureKeys.every(
    (key) => summary.validation[key].length === 0
  );
}

async function main() {
  const options = parseArgs(process.argv.slice(2));
  if (options.help) {
    console.log(`Usage:
  node scripts/directus-marketing-v2-migrate.js [--dry-run|--assert-idempotent] [--env-file <path>] [--storefront-root <path>]

The migration is additive and idempotent. It copies legacy reference keys into
typed picker fields, imports current static legal documents, connects FAQ/legal
blocks, completes the site announcement relation, and adds the standard homepage
campaign slot without deleting or renaming existing content.

The --assert-idempotent option performs a dry run and fails if it would write
anything. It is intended for the post-apply verification step.`);
    return;
  }
  const env = { ...loadEnvFile(options.envFile), ...process.env };
  const config = {
    baseUrl: normalizeBaseUrl(
      env.DIRECTUS_BASE_URL || env.DIRECTUS_PUBLIC_URL || env.PUBLIC_URL || 'http://localhost:8055'
    ),
    token: env.DIRECTUS_SCHEMA_ADMIN_TOKEN || env.DIRECTUS_ADMIN_TOKEN || env.ADMIN_TOKEN || '',
    email: env.DIRECTUS_ADMIN_EMAIL || env.ADMIN_EMAIL || '',
    password: env.DIRECTUS_ADMIN_PASSWORD || env.ADMIN_PASSWORD || '',
    dryRun: options.dryRun,
  };
  const authorization = await login(config);
  const client = createClient(config, authorization);
  const collectionNames = [
    'page',
    'page_sections',
    'page_section_items',
    'page_section_banners',
    'page_section_faqs',
    'page_section_legal_documents',
    'faq',
    'legal_documents',
    'site_settings',
    'banner',
    'campaign',
    'navigation',
    'navigation_items',
    'storefront_collection',
    'storefront_collection_item',
  ];
  const entries = await Promise.all(
    collectionNames.map(async (name) => [name, await client.list(name)])
  );
  const collections = Object.fromEntries(entries);
  collections.directus_files = await client.listFiles();
  const summary = createSummary();
  summary.recordCounts.before = Object.fromEntries(
    collectionNames.map((name) => [name, collections[name].length])
  );
  summary.page_sections.scanned = collections.page_sections.length;

  await migrateTypedReferences(client, summary, collections);
  await importStaticLegalDocuments(
    client,
    summary,
    collections,
    options.storefrontRoot,
    env
  );
  await ensureSiteSettingsAnnouncement(client, summary, collections);
  await ensureHomeCampaignSlot(client, summary, collections);
  await ensureSectionRelations(client, summary, collections);
  validateMedia(collections, summary);
  validateContent(collections, summary);
  summary.recordCounts.after = Object.fromEntries(
    collectionNames.map((name) => [name, collections[name].length])
  );

  console.log(JSON.stringify({
    mode: options.dryRun ? 'dry-run' : 'apply',
    idempotencyKey: HOME_SLOT_MIGRATION_KEY,
    summary,
  }, null, 2));
  if (!summary.validation.ok) {
    process.exitCode = 2;
  } else if (options.assertIdempotent && plannedMutationCount(summary) > 0) {
    console.error(
      `Migration is not idempotent: ${plannedMutationCount(summary)} write(s) remain.`
    );
    process.exitCode = 3;
  }
}

if (require.main === module) {
  main().catch((error) => {
    console.error(error instanceof Error ? error.message : error);
    process.exitCode = 1;
  });
}

module.exports = {
  HOME_SLOT_MIGRATION_KEY,
  LEGAL_DOCUMENT_SOURCES,
  applyTokens,
  copyCollectionItemReference,
  copyTypedReference,
  createSummary,
  normalizeKind,
  plannedMutationCount,
  validateContent,
  validateMedia,
};
