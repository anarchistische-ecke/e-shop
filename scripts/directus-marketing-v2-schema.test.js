const assert = require('node:assert/strict');
const test = require('node:test');

const snapshot = require('../directus/schema/schema.snapshot.json');

test('field validation metadata uses complete Directus filter objects', () => {
  const invalid = snapshot.fields
    .filter((field) => field.meta?.validation)
    .filter((field) => {
      const validation = field.meta.validation;
      return !Array.isArray(validation?._and) && !Array.isArray(validation?._or);
    })
    .map((field) => `${field.collection}.${field.field}`);

  assert.deepEqual(
    invalid,
    [],
    'bare operator filters cause Directus item writes to recurse in generateJoi'
  );
});

test('cross-field date windows are delegated to the marketing validation hook', () => {
  const invalid = snapshot.fields
    .filter((field) => ['campaign', 'banner'].includes(field.collection))
    .filter((field) => field.field === 'active_to')
    .filter((field) => JSON.stringify(field.meta?.validation || {}).includes('$CURRENT'))
    .map((field) => `${field.collection}.${field.field}`);

  assert.deepEqual(
    invalid,
    [],
    'Directus 11.17 passes cross-field $CURRENT values to Joi as invalid date literals'
  );
});

test('rich-text interfaces expose only the constrained editorial toolbar', () => {
  const richTextFields = snapshot.fields
    .filter((field) => field.meta?.interface === 'input-rich-text-html');
  const forbiddenTools = new Set([
    'code',
    'customImage',
    'customMedia',
    'fontfamily',
    'fontsize',
    'forecolor',
    'backcolor',
    'table',
  ]);

  assert.ok(richTextFields.length >= 4);
  richTextFields.forEach((field) => {
    assert.ok(Array.isArray(field.meta.options?.toolbar));
    assert.equal(
      field.meta.options.toolbar.some((tool) => forbiddenTools.has(tool)),
      false,
      `${field.collection}.${field.field} exposes a forbidden rich-text tool`
    );
  });
});

test('content folders remain metadata-only and outside the schema snapshot', () => {
  const collectionNames = new Set(
    snapshot.collections.map((collection) => collection.collection)
  );

  assert.equal(collectionNames.has('cms_marketing'), false);
  assert.equal(collectionNames.has('cms_site_content'), false);
  assert.equal(
    snapshot.collections.find((collection) => collection.collection === 'campaign')?.meta?.group,
    'cms_marketing'
  );
  assert.equal(
    snapshot.collections.find((collection) => collection.collection === 'page')?.meta?.group,
    'cms_site_content'
  );
});

test('nested page-section editor fields remain visible in Directus 11.17.2 drawers', () => {
  const pageSectionFields = new Map(
    snapshot.fields
      .filter((field) => field.collection === 'page_sections')
      .map((field) => [field.field, field])
  );
  const groupAliases = [
    'content_group',
    'media_group',
    'actions_group',
    'references_group',
  ];
  const flattenedFields = [
    'eyebrow',
    'title',
    'accent',
    'body',
    'image',
    'image_alt',
    'mobile_image',
    'mobile_image_alt',
    'primary_cta_label',
    'primary_cta_url',
    'secondary_cta_label',
    'secondary_cta_url',
    'campaign_placement',
    'item_limit',
    'storefront_collection',
    'banners',
    'faqs',
    'legal_documents',
    'items',
  ];

  groupAliases.forEach((fieldName) => {
    assert.equal(pageSectionFields.get(fieldName)?.meta?.hidden, true);
  });
  flattenedFields.forEach((fieldName) => {
    assert.equal(
      pageSectionFields.get(fieldName)?.meta?.group,
      null,
      `${fieldName} must not be hidden inside an alias group in a nested drawer`
    );
  });

  for (const fieldName of ['campaign_placement', 'item_limit']) {
    const field = pageSectionFields.get(fieldName);
    assert.equal(field?.meta?.hidden, true);
    assert.equal(field?.meta?.conditions?.[0]?.hidden, false);
    assert.equal(field?.meta?.conditions?.[0]?.required, true);
    assert.deepEqual(
      field?.meta?.conditions?.[0]?.rule?.section_type?._in,
      ['campaign_slot']
    );
  }
});
