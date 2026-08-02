const test = require('node:test');
const assert = require('node:assert/strict');
const {
  DEFAULT_CONTENT_MANAGER_ROLE,
  presetDefinitions,
} = require('./directus-marketing-v2-presets');

test('provides stable role-scoped Marketing V2 bookmarks', () => {
  const presets = presetDefinitions(DEFAULT_CONTENT_MANAGER_ROLE);
  assert.equal(presets.length, 8);
  assert.equal(new Set(
    presets.map((preset) => `${preset.collection}:${preset.bookmark}`)
  ).size, presets.length);
  assert.ok(presets.every(
    (preset) =>
      preset.role === DEFAULT_CONTENT_MANAGER_ROLE
      && preset.layout === 'tabular'
      && preset.layout_query?.tabular?.limit === 25
  ));
});

test('active campaign bookmark uses inclusive start and exclusive end filters', () => {
  const active = presetDefinitions(DEFAULT_CONTENT_MANAGER_ROLE)[0];
  const serialized = JSON.stringify(active.filter);
  assert.match(serialized, /active_from/);
  assert.match(serialized, /_lte/);
  assert.match(serialized, /active_to/);
  assert.match(serialized, /_gt/);
  assert.doesNotMatch(serialized, /_gte/);
});
