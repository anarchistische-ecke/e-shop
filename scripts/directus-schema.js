#!/usr/bin/env node

const fs = require('node:fs');
const path = require('node:path');

const ROOT_DIR = path.resolve(__dirname, '..');
const DEFAULT_ENV_FILE = path.join(ROOT_DIR, 'directus', '.env');
const DEFAULT_SNAPSHOT_PATH = path.join(ROOT_DIR, 'directus', 'schema', 'schema.snapshot.json');
const DEFAULT_CMS_COLLECTIONS = [
  'site_settings',
  'navigation',
  'navigation_items',
  'page',
  'page_sections',
  'page_section_items',
  'faq',
  'legal_documents',
  'banner',
  'post',
];
const FORBIDDEN_COMMERCE_COLLECTIONS = [
  'brand',
  'cart',
  'cart_item',
  'category',
  'checkout',
  'consent',
  'customer',
  'customer_order',
  'inventory',
  'order',
  'order_checkout_attempt',
  'order_item',
  'payment',
  'payment_refund',
  'price',
  'product',
  'product_category',
  'product_image',
  'product_variant',
  'refund',
  'saved_payment_method',
  'shipment',
  'stock',
  'stock_adjustment',
  'variant',
];

async function main() {
  const { command, options } = parseArgs(process.argv.slice(2));

  if (!command || command === 'help' || command === '--help' || command === '-h') {
    printHelp();
    process.exit(command ? 0 : 1);
  }

  if (!['snapshot', 'diff', 'apply', 'check', 'validate'].includes(command)) {
    throw new Error(`Unsupported command "${command}".`);
  }

  const config = loadConfig(options);

  if (command === 'validate') {
    const snapshot = readSnapshot(config.snapshotPath);
    validateSnapshotBoundary(snapshot, config);
    console.log(`Validated Directus CMS boundary for ${config.snapshotPath}`);
    return;
  }

  await waitForDirectus(config.baseUrl);
  const authHeader = await getAuthHeader(config);

  if (command === 'snapshot') {
    const snapshot = await requestJson(config.baseUrl, authHeader, 'GET', '/schema/snapshot');
    validateSnapshotBoundary(snapshot, config);
    ensureParentDir(config.snapshotPath);
    fs.writeFileSync(config.snapshotPath, `${JSON.stringify(snapshot, null, 2)}\n`, 'utf8');
    console.log(`Wrote Directus schema snapshot to ${config.snapshotPath}`);
    return;
  }

  const snapshot = readSnapshot(config.snapshotPath);
  validateSnapshotBoundary(snapshot, config);
  const diffResponse = await requestJson(
    config.baseUrl,
    authHeader,
    'POST',
    `/schema/diff${options.force ? '?force=true' : ''}`,
    snapshot
  );

  if (!diffResponse || !hasChanges(diffResponse)) {
    console.log(`No Directus schema changes detected against ${config.snapshotPath}`);
    return;
  }

  const summary = summarizeDiff(diffResponse);
  console.log(summary);

  if (command === 'check') {
    process.exitCode = 1;
    return;
  }

  if (command === 'diff') {
    return;
  }

  await requestNoContent(config.baseUrl, authHeader, 'POST', '/schema/apply', diffResponse);
  console.log(`Applied Directus schema snapshot from ${config.snapshotPath}`);
}

function parseArgs(argv) {
  const options = {
    envFile: process.env.DIRECTUS_ENV_FILE || DEFAULT_ENV_FILE,
    snapshotPath: process.env.DIRECTUS_SCHEMA_SNAPSHOT_PATH || DEFAULT_SNAPSHOT_PATH,
    force: false,
  };

  let command = '';

  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];

    if (!command && !arg.startsWith('--')) {
      command = arg;
      continue;
    }

    if (arg === '--env-file') {
      index += 1;
      options.envFile = requireValue(argv[index], '--env-file');
      continue;
    }

    if (arg === '--snapshot') {
      index += 1;
      options.snapshotPath = requireValue(argv[index], '--snapshot');
      continue;
    }

    if (arg === '--force') {
      options.force = true;
      continue;
    }

    throw new Error(`Unsupported argument "${arg}".`);
  }

  options.envFile = path.resolve(options.envFile);
  options.snapshotPath = path.resolve(options.snapshotPath);

  return { command, options };
}

function requireValue(value, flag) {
  if (!value) {
    throw new Error(`Missing value for ${flag}.`);
  }

  return value;
}

function printHelp() {
  console.log(`Usage:
  node scripts/directus-schema.js snapshot [--env-file <path>] [--snapshot <path>]
  node scripts/directus-schema.js diff [--env-file <path>] [--snapshot <path>] [--force]
  node scripts/directus-schema.js check [--env-file <path>] [--snapshot <path>] [--force]
  node scripts/directus-schema.js apply [--env-file <path>] [--snapshot <path>] [--force]
  node scripts/directus-schema.js validate [--env-file <path>] [--snapshot <path>]`);
}

function loadConfig(options) {
  const fileEnv = loadEnvFile(options.envFile);
  const env = { ...fileEnv, ...process.env };
  const baseUrl = normalizeBaseUrl(
    env.DIRECTUS_BASE_URL || env.DIRECTUS_PUBLIC_URL || env.PUBLIC_URL || 'http://localhost:8055'
  );
  const schemaAdminToken = env.DIRECTUS_SCHEMA_ADMIN_TOKEN || env.DIRECTUS_ADMIN_TOKEN || env.ADMIN_TOKEN || '';
  const adminEmail = env.DIRECTUS_ADMIN_EMAIL || env.ADMIN_EMAIL || '';
  const adminPassword = env.DIRECTUS_ADMIN_PASSWORD || env.ADMIN_PASSWORD || '';

  return {
    baseUrl,
    cmsContentCollections: parseCsv(env.DIRECTUS_CMS_CONTENT_COLLECTIONS || DEFAULT_CMS_COLLECTIONS.join(',')),
    snapshotPath: options.snapshotPath,
    schemaAdminToken,
    adminEmail,
    adminPassword,
  };
}

function loadEnvFile(envFile) {
  if (!envFile || !fs.existsSync(envFile)) {
    return {};
  }

  const parsed = {};
  const lines = fs.readFileSync(envFile, 'utf8').split(/\r?\n/);

  for (const rawLine of lines) {
    const line = rawLine.trim();

    if (!line || line.startsWith('#')) {
      continue;
    }

    const separatorIndex = line.indexOf('=');
    if (separatorIndex === -1) {
      continue;
    }

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
      if (response.ok) {
        return;
      }
    } catch (error) {
      // Keep waiting.
    }

    await new Promise((resolve) => setTimeout(resolve, 2000));
  }

  throw new Error(`Timed out waiting for Directus at ${baseUrl}/server/health`);
}

async function getAuthHeader(config) {
  if (config.schemaAdminToken) {
    return `Bearer ${config.schemaAdminToken}`;
  }

  if (!config.adminEmail || !config.adminPassword) {
    throw new Error(
      'Directus schema scripts require DIRECTUS_SCHEMA_ADMIN_TOKEN or DIRECTUS_ADMIN_EMAIL/DIRECTUS_ADMIN_PASSWORD.'
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

function readSnapshot(snapshotPath) {
  if (!fs.existsSync(snapshotPath)) {
    throw new Error(`Missing schema snapshot: ${snapshotPath}`);
  }

  return JSON.parse(fs.readFileSync(snapshotPath, 'utf8'));
}

function parseCsv(value) {
  return [...new Set(String(value || '')
    .split(',')
    .map((entry) => entry.trim())
    .filter(Boolean))]
    .sort();
}

function validateSnapshotBoundary(snapshot, config) {
  if (!snapshot || !Array.isArray(snapshot.collections)) {
    throw new Error(`Invalid Directus schema snapshot: missing collections array in ${config.snapshotPath}`);
  }

  const snapshotCollections = snapshot.collections
    .map((entry) => normalizeCollectionName(entry))
    .filter(Boolean)
    .sort();
  const allowedCollections = [...config.cmsContentCollections].sort();
  const unexpectedCollections = snapshotCollections.filter((name) => !allowedCollections.includes(name));
  const missingCollections = allowedCollections.filter((name) => !snapshotCollections.includes(name));
  const forbiddenCollections = snapshotCollections.filter(isForbiddenCommerceCollection);

  if (unexpectedCollections.length === 0 && missingCollections.length === 0 && forbiddenCollections.length === 0) {
    return;
  }

  const problems = [];
  if (unexpectedCollections.length > 0) {
    problems.push(`unexpected collections: ${unexpectedCollections.join(', ')}`);
  }
  if (missingCollections.length > 0) {
    problems.push(`missing allowlisted collections: ${missingCollections.join(', ')}`);
  }
  if (forbiddenCollections.length > 0) {
    problems.push(`forbidden commerce collections: ${forbiddenCollections.join(', ')}`);
  }

  throw new Error(
    `Directus CMS boundary validation failed for ${config.snapshotPath}: ${problems.join('; ')}. ` +
    `Allowed CMS collections: ${allowedCollections.join(', ')}`
  );
}

function normalizeCollectionName(entry) {
  if (!entry || typeof entry !== 'object') {
    return '';
  }

  const collectionName = typeof entry.collection === 'string' ? entry.collection : '';
  const schemaName = typeof entry.schema?.name === 'string' ? entry.schema.name : '';

  return (collectionName || schemaName).trim();
}

function isForbiddenCommerceCollection(collectionName) {
  const normalized = String(collectionName || '').trim().toLowerCase();

  if (!normalized) {
    return false;
  }

  return FORBIDDEN_COMMERCE_COLLECTIONS.includes(normalized);
}

function ensureParentDir(filePath) {
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
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

function extractChangeSet(diffResponse) {
  return diffResponse?.diff || diffResponse || {};
}

function hasChanges(diffResponse) {
  const changeSet = extractChangeSet(diffResponse);

  return ['collections', 'fields', 'relations']
    .map((key) => changeSet[key])
    .some((value) => Array.isArray(value) && value.length > 0);
}

function summarizeDiff(diffResponse) {
  const changeSet = extractChangeSet(diffResponse);
  const lines = ['Directus schema changes detected:'];

  lines.push(formatChangeBucket('collections', changeSet.collections));
  lines.push(formatChangeBucket('fields', changeSet.fields));
  lines.push(formatChangeBucket('relations', changeSet.relations));

  return lines.join('\n');
}

function formatChangeBucket(label, entries) {
  if (!Array.isArray(entries) || entries.length === 0) {
    return `- ${label}: none`;
  }

  const previews = entries.slice(0, 5).map(formatEntryPreview);
  const suffix = entries.length > 5 ? ` (+${entries.length - 5} more)` : '';
  return `- ${label}: ${entries.length} ${previews.join(', ')}${suffix}`;
}

function formatEntryPreview(entry) {
  if (!entry || typeof entry !== 'object') {
    return 'unknown';
  }

  const kind = entry.kind || entry.type || 'change';
  const collection = entry.collection || entry.collection_a || entry.many_collection || entry.one_collection || '';
  const field = entry.field || entry.many_field || entry.one_field || '';
  const target = [collection, field].filter(Boolean).join('.');

  return target ? `${kind}:${target}` : kind;
}

main().catch((error) => {
  console.error(error.message || error);
  process.exit(1);
});
