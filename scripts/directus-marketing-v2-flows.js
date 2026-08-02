#!/usr/bin/env node

const fs = require('node:fs');
const path = require('node:path');

const ROOT_DIR = path.resolve(__dirname, '..');
const DEFAULT_ENV_FILE = path.join(ROOT_DIR, 'directus', '.env');
const EVENT_FLOW_ID = '4c4cc8d0-9b7f-4d56-84d2-1d64f5f50001';
const EVENT_OPERATION_ID = '4c4cc8d0-9b7f-4d56-84d2-1d64f5f50002';
const SCHEDULE_FLOW_ID = '4c4cc8d0-9b7f-4d56-84d2-1d64f5f50003';
const SCHEDULE_OPERATION_ID = '4c4cc8d0-9b7f-4d56-84d2-1d64f5f50004';
const CMS_COLLECTIONS = [
  'site_settings',
  'navigation',
  'navigation_items',
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
  'storefront_collection',
  'storefront_collection_item',
];

function parseEnv(file) {
  if (!fs.existsSync(file)) return {};
  return Object.fromEntries(
    fs.readFileSync(file, 'utf8')
      .split(/\r?\n/)
      .map((line) => line.trim())
      .filter((line) => line && !line.startsWith('#') && line.includes('='))
      .map((line) => {
        const index = line.indexOf('=');
        const key = line.slice(0, index).trim();
        let value = line.slice(index + 1).trim();
        if (
          (value.startsWith('"') && value.endsWith('"'))
          || (value.startsWith("'") && value.endsWith("'"))
        ) value = value.slice(1, -1);
        return [key, value];
      })
  );
}

function normalizeBaseUrl(value) {
  return String(value || '').replace(/\/+$/, '');
}

function flowDefinitions({ backendBaseUrl, bridgeToken }) {
  const requestOptions = {
    method: 'POST',
    url: `${backendBaseUrl}/internal/directus/content/cache/invalidate`,
    headers: [
      { header: 'Content-Type', value: 'application/json' },
      { header: 'X-Directus-Bridge-Token', value: bridgeToken },
    ],
    body: { scope: 'all' },
  };
  return [
    {
      flow: {
        id: EVENT_FLOW_ID,
        name: 'Marketing V2 — invalidate after content changes',
        icon: 'cached',
        description: 'Immediately invalidates backend facade caches after CMS item changes.',
        status: 'active',
        trigger: 'event',
        accountability: '$full',
        options: {
          type: 'action',
          scope: ['items.create', 'items.update', 'items.delete'],
          collections: CMS_COLLECTIONS,
        },
      },
      operation: {
        id: EVENT_OPERATION_ID,
        name: 'Invalidate backend CMS cache',
        key: 'invalidate_backend_cms_cache',
        type: 'request',
        position_x: 19,
        position_y: 1,
        options: requestOptions,
      },
    },
    {
      flow: {
        id: SCHEDULE_FLOW_ID,
        name: 'Marketing V2 — refresh campaign boundaries',
        icon: 'schedule',
        description: 'Minute-level refresh so campaign start/end boundaries never remain cached.',
        status: 'active',
        trigger: 'schedule',
        accountability: '$full',
        options: { cron: '0 * * * * *' },
      },
      operation: {
        id: SCHEDULE_OPERATION_ID,
        name: 'Refresh campaign cache boundaries',
        key: 'refresh_campaign_cache_boundaries',
        type: 'request',
        position_x: 19,
        position_y: 1,
        options: requestOptions,
      },
    },
  ];
}

async function parseResponse(response) {
  const raw = await response.text();
  const payload = raw ? JSON.parse(raw) : null;
  if (!response.ok) {
    throw new Error(payload?.errors?.[0]?.message || `Directus request failed: ${response.status}`);
  }
  return payload;
}

function createApi(baseUrl, token) {
  async function request(method, pathname, body) {
    return parseResponse(await fetch(`${baseUrl}${pathname}`, {
      method,
      headers: {
        Authorization: `Bearer ${token}`,
        Accept: 'application/json',
        ...(body ? { 'Content-Type': 'application/json' } : {}),
      },
      ...(body ? { body: JSON.stringify(body) } : {}),
    }));
  }
  return {
    get: (pathname) => request('GET', pathname),
    post: (pathname, body) => request('POST', pathname, body),
    patch: (pathname, body) => request('PATCH', pathname, body),
  };
}

async function resolveAdminToken(apiBaseUrl, env) {
  const configured = env.DIRECTUS_SCHEMA_ADMIN_TOKEN || env.DIRECTUS_ADMIN_TOKEN || env.ADMIN_TOKEN;
  if (configured) return configured;
  const email = env.DIRECTUS_ADMIN_EMAIL || env.ADMIN_EMAIL;
  const password = env.DIRECTUS_ADMIN_PASSWORD || env.ADMIN_PASSWORD;
  if (!email || !password) {
    throw new Error('Directus admin credentials are required to provision Flows.');
  }
  const payload = await parseResponse(await fetch(`${apiBaseUrl}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  }));
  return payload.data.access_token;
}

async function upsert(api, endpoint, id, payload) {
  try {
    await api.get(`${endpoint}/${id}`);
    await api.patch(`${endpoint}/${id}`, payload);
    return 'updated';
  } catch (error) {
    if (!/forbidden|not found|404/i.test(String(error?.message || error))) {
      // Directus formats missing singleton lookups differently by release; POST remains idempotent via fixed IDs.
    }
    try {
      await api.post(endpoint, { id, ...payload });
      return 'created';
    } catch (createError) {
      if (/unique|already exists|duplicate/i.test(String(createError?.message || createError))) {
        await api.patch(`${endpoint}/${id}`, payload);
        return 'updated';
      }
      throw createError;
    }
  }
}

async function main() {
  const args = process.argv.slice(2);
  const envIndex = args.indexOf('--env-file');
  const envFile = envIndex >= 0
    ? path.resolve(args[envIndex + 1] || '')
    : path.resolve(process.env.DIRECTUS_ENV_FILE || DEFAULT_ENV_FILE);
  const env = { ...parseEnv(envFile), ...process.env };
  const directusBaseUrl = normalizeBaseUrl(
    env.DIRECTUS_BASE_URL || env.DIRECTUS_PUBLIC_URL || env.PUBLIC_URL || 'http://localhost:8055'
  );
  const backendBaseUrl = normalizeBaseUrl(
    env.DIRECTUS_STOREFRONT_OPS_BACKEND_URL
      || env.STOREFRONT_OPS_BACKEND_URL
      || 'http://host.docker.internal:8080'
  );
  const bridgeToken = env.DIRECTUS_BRIDGE_TOKEN || env.STOREFRONT_OPS_BACKEND_TOKEN;
  if (!bridgeToken) throw new Error('DIRECTUS_BRIDGE_TOKEN is required to provision cache Flows.');

  const adminToken = await resolveAdminToken(directusBaseUrl, env);
  const api = createApi(directusBaseUrl, adminToken);
  const results = [];
  for (const definition of flowDefinitions({ backendBaseUrl, bridgeToken })) {
    const flowStatus = await upsert(
      api,
      '/flows',
      definition.flow.id,
      definition.flow
    );
    const operationPayload = { ...definition.operation, flow: definition.flow.id };
    const operationStatus = await upsert(
      api,
      '/operations',
      definition.operation.id,
      operationPayload
    );
    await api.patch(`/flows/${definition.flow.id}`, {
      operation: definition.operation.id,
    });
    results.push({ name: definition.flow.name, flowStatus, operationStatus });
  }
  console.log(JSON.stringify({ provisioned: results }, null, 2));
}

if (require.main === module) {
  main().catch((error) => {
    console.error(error instanceof Error ? error.message : error);
    process.exitCode = 1;
  });
}

module.exports = { CMS_COLLECTIONS, flowDefinitions };
