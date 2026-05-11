import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import {
  canAccessStorefrontOpsTab,
  resolveStorefrontOpsRoleKind,
} from '../../storefront-ops-access-policy.js';
import { buildStorefrontPreviewUrl } from '../../storefront-ops-preview.js';
import { createStorefrontOpsApi } from '../src/composables/storefrontOpsApi.js';
import {
  compactParams,
  filterCollection,
  formatMinorMoney,
  majorToMinor,
  normalizeSpecificationsForPayload,
} from '../src/storefront-ops-formatters.js';
import {
  STOREFRONT_OPS_TABS,
  createStorefrontOpsLoadingState,
  createStorefrontOpsNavigationCounts,
  isStorefrontOpsMasterDetailTab,
} from '../src/storefront-ops-tabs.js';
import { STOREFRONT_OPS_TAB_PROP_KEYS } from '../src/storefront-ops-tab-props.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const moduleRoot = path.resolve(__dirname, '..');
const sourceRoot = path.join(moduleRoot, 'src');

test('shared tab policy allows content users into editorial catalogue tabs only', () => {
  assert.equal(resolveStorefrontOpsRoleKind({ roleId: '4c4cc8d0-9b7f-4d56-84d2-1d64f5f10008' }), 'content');
  assert.equal(canAccessStorefrontOpsTab('home', 'content'), true);
  assert.equal(canAccessStorefrontOpsTab('products', 'content'), true);
  assert.equal(canAccessStorefrontOpsTab('orders', 'content'), false);
});

test('storefront preview helper builds canonical storefront routes', () => {
  assert.equal(
    buildStorefrontPreviewUrl({
      baseUrl: 'https://shop.example.com/',
      kind: 'product',
      id: '11111111-1111-4111-8111-111111111111',
      key: 'linen-duvet',
    }),
    'https://shop.example.com/product/11111111-1111-4111-8111-111111111111/linen-duvet?cmsPreview=1'
  );
  assert.equal(
    buildStorefrontPreviewUrl({ baseUrl: 'https://shop.example.com', kind: 'category', key: 'bedding' }),
    'https://shop.example.com/category/bedding?cmsPreview=1'
  );
  assert.equal(
    buildStorefrontPreviewUrl({ baseUrl: 'https://shop.example.com', kind: 'page', key: 'delivery' }),
    'https://shop.example.com/info/delivery?cmsPreview=1'
  );
});

test('module tab metadata exposes stable domain tabs and state shapes', () => {
  assert.deepEqual(
    STOREFRONT_OPS_TABS.map((tab) => tab.id),
    ['home', 'products', 'categories', 'brands', 'inventory', 'orders', 'imports', 'promotions', 'tax', 'analytics', 'alerts']
  );
  assert.equal(isStorefrontOpsMasterDetailTab('products'), true);
  assert.equal(isStorefrontOpsMasterDetailTab('analytics'), false);
  assert.equal(createStorefrontOpsNavigationCounts().orders, 0);
  assert.equal(createStorefrontOpsLoadingState().activePromotions, false);
});

test('module API facade keeps bridge and Directus response shapes consistent', async () => {
  const requests = [];
  const api = createStorefrontOpsApi({
    async request(config) {
      requests.push(config);
      if (config.url.startsWith('/storefront-ops-bridge')) {
        return { data: { ok: true, url: config.url } };
      }
      return { data: { data: { id: 'directus-item' } } };
    },
  });

  assert.deepEqual(await api.bridgeRequest('/workspace/products'), {
    ok: true,
    url: '/storefront-ops-bridge/workspace/products',
  });
  assert.deepEqual(await api.directusRequest('/items/pages/home'), { id: 'directus-item' });
  assert.equal(requests[0].method, 'GET');
  assert.equal(requests[1].url, '/items/pages/home');
});

test('module formatter utilities keep bridge params and payloads stable', () => {
  assert.deepEqual(compactParams({ q: 'linen', empty: '', missing: null, page: 2 }), { q: 'linen', page: 2 });
  assert.deepEqual(
    filterCollection([{ name: 'Linen duvet' }, { name: 'Cotton sheet' }], 'duv', [(item) => item.name]),
    [{ name: 'Linen duvet' }]
  );
  assert.equal(formatMinorMoney(123456, 'RUB'), '1 234,56 RUB');
  assert.equal(majorToMinor('19.95'), 1995);
  assert.deepEqual(
    normalizeSpecificationsForPayload([
      { title: 'Care', description: '', items: [{ label: 'Wash', value: 'Cold' }, { label: '', value: 'Ignored' }] },
    ]),
    [{ title: 'Care', description: null, items: [{ label: 'Wash', value: 'Cold' }] }]
  );
});

test('tab button handlers are exposed through the storefront ops prop bridge', () => {
  const propKeys = new Set(STOREFRONT_OPS_TAB_PROP_KEYS);
  const workspaceSource = fs.readFileSync(path.join(sourceRoot, 'composables/useStorefrontOpsWorkspace.js'), 'utf8');
  const viewProps = extractStorefrontOpsViewProps(workspaceSource);
  const componentFiles = [
    path.join(sourceRoot, 'components/StorefrontOpsTabShell.vue'),
    ...fs.readdirSync(path.join(sourceRoot, 'components/tabs'))
      .filter((entry) => entry.endsWith('.vue'))
      .map((entry) => path.join(sourceRoot, 'components/tabs', entry)),
  ];
  const missing = [];

  for (const filePath of componentFiles) {
    const fileName = path.relative(sourceRoot, filePath);
    const source = fs.readFileSync(filePath, 'utf8');
    const localHandlers = extractLocalScriptSetupFunctions(source);
    for (const handler of extractVueEventHandlerNames(source)) {
      if (localHandlers.has(handler)) {
        continue;
      }
      if (!propKeys.has(handler)) {
        missing.push(`${fileName}: ${handler} missing from STOREFRONT_OPS_TAB_PROP_KEYS`);
      }
      if (!viewProps.has(handler)) {
        missing.push(`${fileName}: ${handler} missing from storefrontOpsViewProps`);
      }
    }
  }

  assert.deepEqual(missing, []);
});

test('module action buttons are exposed by the workspace composable', () => {
  const moduleSource = fs.readFileSync(path.join(sourceRoot, 'module.vue'), 'utf8');
  const workspaceSource = fs.readFileSync(path.join(sourceRoot, 'composables/useStorefrontOpsWorkspace.js'), 'utf8');
  const moduleHandlers = extractVueEventHandlerNames(moduleSource);
  const composableReturn = extractComposableReturnKeys(workspaceSource);
  const missing = [...moduleHandlers]
    .filter((handler) => !composableReturn.has(handler))
    .map((handler) => `module.vue: ${handler} missing from useStorefrontOpsWorkspace return`);

  assert.deepEqual(missing, []);
});

function extractVueEventHandlerNames(source) {
  return new Set(
    [...source.matchAll(/@(click|submit)(?:\.[\w-]+)*="([^"]+)"/g)]
      .map((match) => match[2].trim())
      .map((expression) => expression.match(/^([A-Za-z_$][\w$]*)\b/)?.[1])
      .filter(Boolean)
      .filter((name) => name !== '$emit' && name !== 'window')
  );
}

function extractLocalScriptSetupFunctions(source) {
  const script = source.match(/<script\s+setup[^>]*>([\s\S]*?)<\/script>/)?.[1] || '';
  return new Set([...script.matchAll(/\b(?:async\s+)?function\s+([A-Za-z_$][\w$]*)\s*\(/g)].map((match) => match[1]));
}

function extractStorefrontOpsViewProps(source) {
  const block = extractBlockBetween(source, 'const storefrontOpsViewProps = computed(() => ({', '\n  }));\n\n  function tabCount');
  return extractObjectKeys(block);
}

function extractComposableReturnKeys(source) {
  const block = extractBlockBetween(source, '\n  return {\n', '\n  };\n}', { fromEnd: true });
  return extractObjectKeys(block);
}

function extractObjectKeys(block) {
  const shorthandKeys = [...block.matchAll(/^\s*([A-Za-z_$][\w$]*)\s*,\s*$/gm)].map((match) => match[1]);
  const explicitKeys = [...block.matchAll(/^\s*([A-Za-z_$][\w$]*)\s*:/gm)].map((match) => match[1]);
  return new Set([...shorthandKeys, ...explicitKeys]);
}

function extractBlockBetween(source, startToken, endToken, { fromEnd = false } = {}) {
  const start = fromEnd ? source.lastIndexOf(startToken) : source.indexOf(startToken);
  assert.notEqual(start, -1, `Missing start token: ${startToken}`);
  const blockStart = start + startToken.length;
  const end = source.indexOf(endToken, blockStart);
  assert.notEqual(end, -1, `Missing end token: ${endToken}`);
  return source.slice(blockStart, end);
}
