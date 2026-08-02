import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const rootDir = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const legacyCmsCollections = [
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
  'product_overlay',
  'category_overlay',
  'catalogue_overlay_block',
  'catalogue_overlay_block_item',
  'storefront_collection',
  'storefront_collection_item',
].join(',');

test('schema validation upgrades a persisted pre-Marketing-V2 allowlist', () => {
  const result = spawnSync(
    process.execPath,
    [
      'scripts/directus-schema.js',
      'validate',
      '--snapshot',
      'directus/schema/schema.snapshot.json',
    ],
    {
      cwd: rootDir,
      encoding: 'utf8',
      env: {
        ...process.env,
        DIRECTUS_CMS_CONTENT_COLLECTIONS: legacyCmsCollections,
      },
    }
  );

  assert.equal(result.status, 0, result.stderr || result.stdout);
  assert.match(result.stdout, /Validated Directus CMS boundary/);
});

test('governance bootstrap unions required collections with persisted configuration', () => {
  const source = readFileSync(
    resolve(rootDir, 'scripts/directus-governance-bootstrap.sh'),
    'utf8'
  );

  for (const collection of [
    'campaign',
    'page_section_banners',
    'page_section_faqs',
    'page_section_legal_documents',
  ]) {
    assert.match(source, new RegExp(`REQUIRED_CMS_CONTENT_COLLECTIONS="[^"]*${collection}`));
  }
  assert.match(
    source,
    /DIRECTUS_CMS_CONTENT_COLLECTIONS="\$\{DIRECTUS_CMS_CONTENT_COLLECTIONS:\+\$\{DIRECTUS_CMS_CONTENT_COLLECTIONS\},\}\$\{REQUIRED_CMS_CONTENT_COLLECTIONS\}"/
  );
});
