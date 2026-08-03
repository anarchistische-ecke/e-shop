# Directus Marketing V2 Rollout and Acceptance

Use this runbook for staging and production. Marketing V2 is additive: do not
delete or rename existing Directus tables during rollout.

## Required configuration

Set and keep server-side only:

- `DIRECTUS_STATIC_TOKEN`: scoped storefront-reader token;
- `DIRECTUS_PREVIEW_TOKEN`: separate scoped draft/version reader token;
- `CMS_PREVIEW_SECRET`: at least 32 random characters, identical in Directus
  and backend;
- `STOREFRONT_OPS_PREVIEW_BASE_URL`: public storefront origin;
- `DIRECTUS_BRIDGE_TOKEN`: Directus-to-backend cache invalidation token;
- `CMS_MARKETING_V2_ENABLED=false` for the initial deployment;
- `STOREFRONT_OPS_LEGACY_HOME_ENABLED=true` until cutover.

No Directus service token belongs in frontend build/runtime variables.

## Staging sequence

1. Build and deploy the backward-compatible backend and storefront:

   ```bash
   mvn -pl api -am test
   (cd ../cozyhome && npm test && npm run build)
   ```

2. Build and verify extensions:

   ```bash
   ./scripts/directus-extensions-build.sh
   ./scripts/directus-extensions-build.sh --check
   ```

   The runtime bundle must include `directus-hook-marketing-validation`.
   Directus 11.17.2 does not support comparing one date field to another with a
   `$CURRENT` field-validation literal; the hook enforces campaign/banner end
   times and returns a normal 400 error instead of allowing a server error.

3. Take a Directus backup and perform the documented non-destructive restore
   drill.

4. Validate and apply the additive snapshot:

   ```bash
   node scripts/directus-marketing-v2-schema.js
   node scripts/directus-schema.js validate
   ./scripts/directus-schema-apply.sh --env-file /path/to/directus.env
   ```

   In the pinned Directus 11.17.2 line, `list-o2m` item drawers omit fields
   nested inside alias field groups. The committed snapshot therefore keeps
   page-section fields top-level and uses field conditions to show only the
   controls relevant to the selected block type. The alias groups remain
   hidden compatibility metadata until Directus is upgraded and retested.

5. Provision security, role-scoped bookmarks, and cache flows:

   ```bash
   ./scripts/directus-governance-bootstrap.sh --env-file /path/to/stack.env
   node scripts/directus-marketing-v2-presets.js --env-file /path/to/directus.env
   node scripts/directus-marketing-v2-flows.js --env-file /path/to/directus.env
   ```

6. Dry-run migration and retain its JSON report:

   ```bash
   node scripts/directus-marketing-v2-migrate.js \
     --dry-run \
     --env-file /path/to/directus.env \
     --storefront-root /path/to/cozyhome
   ```

   Populate the seller name, legal name, INN, OGRNIP, address, phone, email,
   and site name in `site_settings` (or the migration environment) first. The
   migration refuses to import incomplete commercial legal documents.

   The report must have no duplicate keys, unresolved references, missing alt
   text, invalid routes, dangling relations, or missing published timestamps.

7. Apply the same migration without `--dry-run`, rerun the dry run, and confirm
   the second report proposes zero imports, updates, or new relations.

8. Enable `CMS_MARKETING_V2_ENABLED=true`, keep the legacy Home flag on, and
   restart Directus/backend/storefront. Complete acceptance below.

9. Set `STOREFRONT_OPS_LEGACY_HOME_ENABLED=false` only after acceptance.

## Acceptance

As a Content Manager in real Directus:

- edit and reorder homepage blocks;
- create a named page version and open Live Preview;
- upload desktop/mobile creative images with alt text;
- create a scheduled campaign linked through the promotion picker;
- verify start-inclusive/end-exclusive behavior;
- verify the shown discount matches backend promotion facts;
- update navigation through a page relation;
- attach/reorder FAQ and legal documents;
- change page SEO/robots and global OG defaults;
- verify desktop and mobile layouts and accessibility;
- verify a draft and an expired preview token are inaccessible publicly.

Regression checks:

- every existing custom-module commerce tab except legacy Home;
- promotion calculation, promo-code validation, cart, and checkout;
- façade API/schema contract tests;
- production backend and storefront builds;
- event flow invalidation and the minute schedule flow;
- public Directus item APIs reject anonymous reads;
- service reader can read only published CMS content.
- preview reader can read versions but has no application, schema, user, or
  security access.
- an end time equal to or earlier than its start is rejected with an
  understandable validation message.

Record the backup, restore-drill result, migration report, browser run, build
identifiers, operator, and pilot publish result in the change log.

## Rollback

For functional rollback:

1. Set `CMS_MARKETING_V2_ENABLED=false`.
2. Set `STOREFRONT_OPS_LEGACY_HOME_ENABLED=true`.
3. Restart Directus/backend/storefront and invalidate edge/application caches.
4. Confirm legacy homepage and custom commerce operations.

Leave additive schema and migrated content intact. Restore the Directus backup
only for confirmed data corruption, following the existing restore runbook and
object-storage consistency check.
