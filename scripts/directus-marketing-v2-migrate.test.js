const assert = require('node:assert/strict');
const test = require('node:test');
const {
  HOME_SLOT_MIGRATION_KEY,
  LEGAL_DOCUMENT_SOURCES,
  applyTokens,
  copyCollectionItemReference,
  copyTypedReference,
  createSummary,
  normalizeKind,
  validateContent,
  validateMedia,
} = require('./directus-marketing-v2-migrate');

test('normalizes legacy reference kinds', () => {
  assert.equal(normalizeKind('Product-Slug'), 'product_slug');
  assert.equal(normalizeKind('storefront collection'), 'storefront_collection');
});

test('copies legacy page references without overwriting typed fields', () => {
  assert.deepEqual(
    copyTypedReference({ reference_kind: 'product_slug', reference_key: 'linen-set' }),
    { product_key: 'linen-set' }
  );
  assert.deepEqual(
    copyTypedReference({
      reference_kind: 'product_slug',
      reference_key: 'legacy',
      product_key: 'canonical',
    }),
    {}
  );
});

test('resolves collection keys to stable Directus IDs', () => {
  assert.deepEqual(
    copyTypedReference(
      { reference_kind: 'storefront_collection', reference_key: 'summer' },
      new Map([['summer', 42]])
    ),
    { storefront_collection: 42 }
  );
});

test('copies storefront collection item references idempotently', () => {
  assert.deepEqual(
    copyCollectionItemReference({ entity_kind: 'category', entity_key: 'bedroom' }),
    { category_key: 'bedroom' }
  );
  assert.deepEqual(
    copyCollectionItemReference({
      entity_kind: 'category',
      entity_key: 'bedroom',
      category_key: 'bedroom',
    }),
    {}
  );
});

test('uses one stable migration key for homepage slot reruns', () => {
  assert.equal(HOME_SLOT_MIGRATION_KEY, 'marketing-v2:home:campaign-slot');
});

test('defines each current storefront legal document once', () => {
  assert.equal(LEGAL_DOCUMENT_SOURCES.length, 6);
  assert.equal(new Set(LEGAL_DOCUMENT_SOURCES.map((document) => document.key)).size, 6);
  assert.equal(new Set(LEGAL_DOCUMENT_SOURCES.map((document) => document.path)).size, 6);
});

test('resolves legal template tokens before importing rich text', () => {
  assert.equal(
    applyTokens('<p>{{LEGAL_ENTITY_SHORT}} · {{SITE_URL}}</p>', {
      LEGAL_ENTITY_SHORT: 'ИП Тест',
      SITE_URL: 'https://example.test',
    }),
    '<p>ИП Тест · https://example.test</p>'
  );
});

test('migration validation accepts preserved relations, routes, media and timestamps', () => {
  const summary = createSummary();
  const collections = {
    page: [{
      id: 1,
      slug: 'home',
      path: '/',
      status: 'published',
      published_at: '2026-08-01T00:00:00Z',
    }],
    page_sections: [{
      id: 2,
      page: 1,
      status: 'published',
      published_at: '2026-08-01T00:00:00Z',
    }],
    page_section_items: [],
    page_section_faqs: [],
    page_section_legal_documents: [],
    faq: [],
    legal_documents: [],
    banner: [],
    campaign: [],
    navigation: [],
    navigation_items: [],
    storefront_collection: [],
    storefront_collection_item: [],
    site_settings: [],
    directus_files: [],
  };

  validateMedia(collections, summary);
  validateContent(collections, summary);

  assert.equal(summary.validation.ok, true);
  assert.deepEqual(summary.validation.danglingRelations, []);
});

test('migration validation reports invalid preserved content before cutover', () => {
  const summary = createSummary();
  const collections = {
    page: [
      {
        id: 1,
        slug: 'duplicate',
        path: '//unsafe.example',
        status: 'published',
        published_at: null,
        seo_image: 'missing-file',
      },
      { id: 2, slug: 'duplicate', path: '/safe', status: 'draft' },
    ],
    page_sections: [{ id: 3, page: 999, status: 'draft' }],
    page_section_items: [],
    page_section_faqs: [],
    page_section_legal_documents: [],
    faq: [],
    legal_documents: [],
    banner: [],
    campaign: [],
    navigation: [],
    navigation_items: [],
    storefront_collection: [],
    storefront_collection_item: [],
    site_settings: [],
    directus_files: [],
  };

  validateMedia(collections, summary);
  validateContent(collections, summary);

  assert.equal(summary.validation.ok, false);
  assert.deepEqual(summary.validation.duplicateKeys, ['page.slug:duplicate']);
  assert.equal(summary.validation.invalidRoutes.length, 1);
  assert.equal(summary.validation.missingMediaReferences.length, 1);
  assert.equal(summary.validation.danglingRelations.length, 1);
  assert.equal(summary.validation.publishedWithoutTimestamp.length, 1);
});
