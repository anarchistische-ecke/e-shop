const assert = require('node:assert/strict');
const test = require('node:test');
const { CMS_COLLECTIONS, flowDefinitions } = require('./directus-marketing-v2-flows');

test('provisions event and minute schedule flows with authenticated backend requests', () => {
  const definitions = flowDefinitions({
    backendBaseUrl: 'http://backend:8080',
    bridgeToken: 'secret',
  });
  assert.equal(definitions.length, 2);
  assert.deepEqual(definitions[0].flow.options.scope, [
    'items.create',
    'items.update',
    'items.delete',
  ]);
  assert.equal(definitions[1].flow.options.cron, '0 * * * * *');
  definitions.forEach(({ operation }) => {
    assert.equal(
      operation.options.url,
      'http://backend:8080/internal/directus/content/cache/invalidate'
    );
    assert.equal(
      operation.options.headers.find((header) => header.header === 'X-Directus-Bridge-Token').value,
      'secret'
    );
  });
});

test('watches every Marketing V2 content collection', () => {
  assert.ok(CMS_COLLECTIONS.includes('campaign'));
  assert.ok(CMS_COLLECTIONS.includes('page_section_faqs'));
  assert.ok(CMS_COLLECTIONS.includes('legal_documents'));
});
