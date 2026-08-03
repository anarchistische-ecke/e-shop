import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import test from 'node:test';

const require = createRequire(import.meta.url);
const {
  createSummary,
  plannedMutationCount,
} = require('../directus-marketing-v2-migrate.js');

test('plannedMutationCount is zero for a settled migration', () => {
  assert.equal(plannedMutationCount(createSummary()), 0);
});

test('plannedMutationCount includes every migration write counter', () => {
  const summary = createSummary();
  summary.page_section_items.updated = 1;
  summary.storefront_collection_items.updated = 2;
  summary.page_sections.created = 3;
  summary.page_section_faqs.created = 4;
  summary.page_section_legal_documents.created = 5;
  summary.legal_documents.imported = 6;
  summary.site_settings.announcementLinked = 7;

  assert.equal(plannedMutationCount(summary), 28);
});
