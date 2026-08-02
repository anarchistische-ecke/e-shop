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
