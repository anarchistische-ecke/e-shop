#!/usr/bin/env node

const fs = require('node:fs');
const path = require('node:path');

const ROOT_DIR = path.resolve(__dirname, '..');
const DEFAULT_ENV_FILE = path.join(ROOT_DIR, 'directus', '.env');
const DEFAULT_CONTENT_MANAGER_ROLE =
  '4c4cc8d0-9b7f-4d56-84d2-1d64f5f10008';

function parseEnv(file) {
  if (!fs.existsSync(file)) return {};
  return Object.fromEntries(
    fs.readFileSync(file, 'utf8')
      .split(/\r?\n/)
      .map((line) => line.trim())
      .filter((line) => line && !line.startsWith('#') && line.includes('='))
      .map((line) => {
        const separator = line.indexOf('=');
        const key = line.slice(0, separator).trim();
        let value = line.slice(separator + 1).trim();
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

function tabular(fields, sort) {
  return {
    layout: 'tabular',
    layout_query: {
      tabular: {
        fields,
        sort,
        limit: 25,
      },
    },
  };
}

function presetDefinitions(role) {
  const campaignFields = [
    'internal_name',
    'status',
    'priority',
    'active_from',
    'active_to',
    'operational_link_type',
    'landing_page',
  ];
  const activeWindow = {
    _and: [
      { status: { _eq: 'published' } },
      {
        _or: [
          { active_from: { _null: true } },
          { active_from: { _lte: '$NOW' } },
        ],
      },
      {
        _or: [
          { active_to: { _null: true } },
          { active_to: { _gt: '$NOW' } },
        ],
      },
    ],
  };
  return [
    {
      bookmark: 'Кампании · активные сейчас',
      collection: 'campaign',
      role,
      icon: 'campaign',
      color: '#2E7D32',
      filter: activeWindow,
      ...tabular(campaignFields, ['-priority', 'sort', 'id']),
    },
    {
      bookmark: 'Кампании · запланированные',
      collection: 'campaign',
      role,
      icon: 'schedule',
      color: '#EF6C00',
      filter: {
        _and: [
          { status: { _eq: 'published' } },
          { active_from: { _gt: '$NOW' } },
        ],
      },
      ...tabular(campaignFields, ['active_from', '-priority', 'id']),
    },
    {
      bookmark: 'Кампании · черновики',
      collection: 'campaign',
      role,
      icon: 'edit_note',
      color: '#546E7A',
      filter: { status: { _in: ['draft', 'in_review'] } },
      ...tabular(campaignFields, ['-date_updated', 'id']),
    },
    {
      bookmark: 'Баннеры · по размещению',
      collection: 'banner',
      role,
      icon: 'view_carousel',
      color: '#6A1B9A',
      filter: { status: { _neq: 'archived' } },
      ...tabular(
        [
          'internal_name',
          'status',
          'placement',
          'campaign',
          'priority',
          'active_from',
          'active_to',
        ],
        ['placement', '-priority', 'sort', 'id']
      ),
    },
    {
      bookmark: 'Страницы · опубликованные',
      collection: 'page',
      role,
      icon: 'web',
      color: '#1565C0',
      filter: { status: { _eq: 'published' } },
      ...tabular(
        ['title', 'slug', 'path', 'template', 'published_at', 'date_updated'],
        ['sort', 'title', 'id']
      ),
    },
    {
      bookmark: 'Страницы · требуют работы',
      collection: 'page',
      role,
      icon: 'rate_review',
      color: '#C62828',
      filter: { status: { _in: ['draft', 'in_review'] } },
      ...tabular(
        ['title', 'status', 'slug', 'template', 'date_updated'],
        ['-date_updated', 'id']
      ),
    },
    {
      bookmark: 'Юридические документы · опубликованные',
      collection: 'legal_documents',
      role,
      icon: 'gavel',
      color: '#37474F',
      filter: { status: { _eq: 'published' } },
      ...tabular(
        ['title', 'document_key', 'version_label', 'effective_from', 'published_at'],
        ['sort', 'title', 'id']
      ),
    },
    {
      bookmark: 'Подборки витрины · опубликованные',
      collection: 'storefront_collection',
      role,
      icon: 'view_quilt',
      color: '#00838F',
      filter: { status: { _eq: 'published' } },
      ...tabular(
        ['title', 'key', 'status', 'published_at', 'date_updated'],
        ['sort', 'title', 'id']
      ),
    },
  ];
}

async function parseResponse(response) {
  const raw = await response.text();
  const payload = raw ? JSON.parse(raw) : null;
  if (!response.ok) {
    throw new Error(
      payload?.errors?.[0]?.message
        || `Directus request failed: ${response.status}`
    );
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

async function resolveAdminToken(baseUrl, env) {
  const configured =
    env.DIRECTUS_SCHEMA_ADMIN_TOKEN
    || env.DIRECTUS_ADMIN_TOKEN
    || env.ADMIN_TOKEN;
  if (configured) return configured;
  const email = env.DIRECTUS_ADMIN_EMAIL || env.ADMIN_EMAIL;
  const password = env.DIRECTUS_ADMIN_PASSWORD || env.ADMIN_PASSWORD;
  if (!email || !password) {
    throw new Error('Directus admin credentials are required to provision presets.');
  }
  const payload = await parseResponse(await fetch(`${baseUrl}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  }));
  return payload.data.access_token;
}

async function main() {
  const args = process.argv.slice(2);
  const envIndex = args.indexOf('--env-file');
  const envFile = envIndex >= 0
    ? path.resolve(args[envIndex + 1] || '')
    : path.resolve(process.env.DIRECTUS_ENV_FILE || DEFAULT_ENV_FILE);
  const env = { ...parseEnv(envFile), ...process.env };
  const baseUrl = normalizeBaseUrl(
    env.DIRECTUS_BASE_URL
      || env.DIRECTUS_PUBLIC_URL
      || env.PUBLIC_URL
      || 'http://localhost:8055'
  );
  const role =
    env.DIRECTUS_ROLE_CONTENT_MANAGER_ID || DEFAULT_CONTENT_MANAGER_ROLE;
  const token = await resolveAdminToken(baseUrl, env);
  const api = createApi(baseUrl, token);
  const query = new URLSearchParams({
    limit: '-1',
    fields: 'id,bookmark,collection,role',
    filter: JSON.stringify({ role: { _eq: role } }),
  });
  const existing = (await api.get(`/presets?${query}`))?.data || [];
  const results = [];

  for (const definition of presetDefinitions(role)) {
    const match = existing.find(
      (preset) =>
        preset.collection === definition.collection
        && preset.bookmark === definition.bookmark
        && String(preset.role?.id || preset.role || '') === role
    );
    if (match) {
      await api.patch(`/presets/${match.id}`, definition);
      results.push({ bookmark: definition.bookmark, status: 'updated' });
    } else {
      await api.post('/presets', definition);
      results.push({ bookmark: definition.bookmark, status: 'created' });
    }
  }
  console.log(JSON.stringify({ role, presets: results }, null, 2));
}

if (require.main === module) {
  main().catch((error) => {
    console.error(error instanceof Error ? error.message : error);
    process.exitCode = 1;
  });
}

module.exports = {
  DEFAULT_CONTENT_MANAGER_ROLE,
  presetDefinitions,
};
