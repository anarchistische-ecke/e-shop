#!/usr/bin/env node

const apiBase = normalizeBase(process.env.API_BASE || 'https://api.yug-postel.ru');
const storefrontBase = normalizeBase(process.env.STOREFRONT_BASE || 'https://yug-postel.ru');
const productId = String(process.env.PRODUCT_ID || '').trim();
const pageSize = 96;

const products = await loadAllProducts();
const productById = new Map(products.map((product) => [String(product.id), product]));
const productBySlug = new Map(products.map((product) => [normalizeKey(product.slug), product]));
const categories = await fetchJson(`${apiBase}/categories`);
const categoryById = new Map(categories.map((category) => [String(category.id), category]));
const categoryBySlug = new Map(categories.map((category) => [normalizeKey(category.slug), category]));
const home = await fetchJson(`${apiBase}/content/pages/home`);

await validateHomepageReferences(home);
await assertNoStore('/');
await assertNoStore('/catalog');
await assertNoStore('/catalog?search=publication-smoke');
await assertNoStore('/sitemap.xml');
await assertStaticAssetCaching();

if (productId) {
  await validateProduct(productId);
}

console.log(
  `Storefront consistency check passed: ${products.length} products and ` +
    `${publishedReferences(home).length} homepage references.`
);

async function loadAllProducts() {
  const loaded = [];
  let declaredTotal = null;
  let declaredPages = null;

  for (let page = 0; page < 1000; page += 1) {
    const response = await fetchRequired(`${apiBase}/products?page=${page}&size=${pageSize}`);
    const pageProducts = await response.json();
    assert(Array.isArray(pageProducts), `Products page ${page} is not an array`);

    const responsePage = numberHeader(response, 'x-page');
    const responseTotal = numberHeader(response, 'x-total-count');
    const responsePages = numberHeader(response, 'x-total-pages');
    assert(responsePage === page, `Requested products page ${page}, received page ${responsePage}`);
    if (declaredTotal === null) declaredTotal = responseTotal;
    if (declaredPages === null) declaredPages = responsePages;
    assert(responseTotal === declaredTotal, 'X-Total-Count changed while products were being loaded');
    assert(responsePages === declaredPages, 'X-Total-Pages changed while products were being loaded');

    loaded.push(...pageProducts);
    if (page + 1 >= declaredPages) break;
  }

  const ids = loaded.map((product) => String(product.id || ''));
  assert(ids.every(Boolean), 'A product response has no ID');
  assert(new Set(ids).size === ids.length, 'Product pagination returned duplicate IDs');
  assert(loaded.length === declaredTotal, `Loaded ${loaded.length} products, API declared ${declaredTotal}`);
  return loaded;
}

async function validateHomepageReferences(page) {
  for (const reference of publishedReferences(page)) {
    let target;
    if (reference.kind === 'product_id') target = productById.get(reference.key);
    if (reference.kind === 'product_slug') target = productBySlug.get(normalizeKey(reference.key));
    if (reference.kind === 'category_id') target = categoryById.get(reference.key);
    if (reference.kind === 'category_slug') target = categoryBySlug.get(normalizeKey(reference.key));

    if (reference.kind.startsWith('product_')) {
      assert(target, `Homepage product reference does not resolve: ${reference.kind}:${reference.key}`);
      assert(target.isActive !== false, `Homepage product reference is inactive: ${reference.key}`);
    } else if (reference.kind.startsWith('category_')) {
      assert(target, `Homepage category reference does not resolve: ${reference.kind}:${reference.key}`);
      assert(target.isActive !== false, `Homepage category reference is inactive: ${reference.key}`);
    } else if (reference.kind === 'storefront_collection') {
      const collection = await fetchJson(`${apiBase}/content/collections/${encodeURIComponent(reference.key)}`);
      if (collection.status !== undefined) {
        assert(normalizeKey(collection.status) === 'published', `Homepage collection is not published: ${reference.key}`);
      }
    }
  }
}

async function validateProduct(id) {
  const direct = await fetchJson(`${apiBase}/products/${encodeURIComponent(id)}`);
  assert(direct.isActive !== false, `Reported product ${id} is inactive`);
  assert(productById.has(id), `Reported product ${id} is absent from the complete catalogue response`);
  assert(normalizeKey(direct.slug) === 'abelard', `Reported product slug is ${direct.slug}, expected abelard`);

  const categorySlug = direct.categories?.[0]?.slug;
  assert(categorySlug, `Reported product ${id} has no category`);
  await assertNoStore(`/category/${encodeURIComponent(categorySlug)}`);
  await assertNoStore(`/product/${encodeURIComponent(id)}/${encodeURIComponent(direct.slug)}`);

  const catalogueHtml = await fetchText(`${storefrontBase}/catalog`);
  assert(catalogueHtml.includes(id), `Reported product ${id} is absent from catalogue SSR data`);
  const categoryHtml = await fetchText(`${storefrontBase}/category/${encodeURIComponent(categorySlug)}`);
  assert(categoryHtml.includes(id), `Reported product ${id} is absent from category SSR data`);
  const sitemap = await fetchText(`${storefrontBase}/sitemap.xml`);
  assert(sitemap.includes(`/product/${id}/abelard`), `Reported product ${id} is absent from sitemap.xml`);
}

function publishedReferences(page) {
  return (page?.sections || []).flatMap((section) => (section?.items || []))
    .map((item) => ({ kind: normalizeKind(item.referenceKind), key: String(item.referenceKey || '').trim() }))
    .filter((reference) => reference.key && [
      'product_id',
      'product_slug',
      'category_id',
      'category_slug',
      'storefront_collection',
    ].includes(reference.kind));
}

function normalizeKind(value) {
  const kind = normalizeKey(value).replaceAll('-', '_').replaceAll(' ', '_');
  if (kind === 'product') return 'product_slug';
  if (kind === 'category') return 'category_slug';
  if (['collection', 'cms_collection', 'collection_key'].includes(kind)) return 'storefront_collection';
  return kind;
}

async function assertNoStore(path) {
  const response = await fetchRequired(`${storefrontBase}${path}`);
  const cacheControl = response.headers.get('cache-control') || '';
  assert(/(?:^|,)\s*no-store(?:\s*(?:,|$))/.test(cacheControl), `${path} is missing Cache-Control: no-store (${cacheControl || 'empty'})`);
}

async function assertStaticAssetCaching() {
  const html = await fetchText(`${storefrontBase}/`);
  const assetPath = html.match(/(?:src|href)=["']([^"']*\/assets\/[^"']+)["']/)?.[1];
  assert(assetPath, 'Storefront HTML does not reference a built static asset');
  const assetUrl = new URL(assetPath, `${storefrontBase}/`).toString();
  const response = await fetchRequired(assetUrl);
  const cacheControl = response.headers.get('cache-control') || '';
  assert(/(?:^|,)\s*public(?:\s*(?:,|$))/.test(cacheControl), `Static asset is not public-cacheable (${cacheControl || 'empty'})`);
  assert(/max-age=31536000/.test(cacheControl), `Static asset is missing a one-year max-age (${cacheControl || 'empty'})`);
  assert(/immutable/.test(cacheControl), `Static asset is missing immutable caching (${cacheControl || 'empty'})`);
}

async function fetchJson(url) {
  const response = await fetchRequired(url);
  return response.json();
}

async function fetchText(url) {
  const response = await fetchRequired(url);
  return response.text();
}

async function fetchRequired(url) {
  const response = await fetch(url, { headers: { Accept: 'application/json, text/html;q=0.9, */*;q=0.8' } });
  assert(response.ok, `${url} returned HTTP ${response.status}`);
  return response;
}

function numberHeader(response, name) {
  const raw = response.headers.get(name);
  const value = Number(raw);
  assert(raw !== null && Number.isInteger(value) && value >= 0, `${name} is missing or invalid (${raw})`);
  return value;
}

function normalizeBase(value) {
  return String(value).replace(/\/+$/, '');
}

function normalizeKey(value) {
  return String(value || '').trim().toLowerCase();
}

function assert(condition, message) {
  if (!condition) throw new Error(message);
}
