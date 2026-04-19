# Directus Rollback Strategy

This document defines how CMS-related changes are reversed in production today, and in staging later when a separate staging target exists.

Use it together with:

- [directus-deployment.md](./directus-deployment.md)
- [directus-operations-runbook.md](./directus-operations-runbook.md)
- [directus-schema-versioning.md](./directus-schema-versioning.md)
- [directus-content-migration.md](./directus-content-migration.md)

## Rollback Units

Not every CMS incident needs the same rollback action.

Use the smallest safe rollback unit first:

1. frontend path rollback
   Switch the storefront back to legacy hard-coded content paths without touching Directus data.
2. content rollback
   Revert editorial content only, either by correcting content in Directus, reapplying a previous committed seed, or restoring the Directus DB.
3. schema rollback
   Revert the Directus schema snapshot by returning to the previous git ref and redeploying, with DB restore when the schema change was destructive.
4. runtime rollback
   Revert the Directus container version or the overall application ref when the outage came from deploy/runtime behavior.

## Prerequisites

The rollback plan depends on these already-established controls:

- pre-deploy Directus PostgreSQL dumps via `scripts/directus-db-backup.sh`
- committed schema snapshots in `directus/schema/schema.snapshot.json`
- committed initial content seeds in `directus/seed/initial-content.js` and `directus/seed/legal/`
- operational restore script `scripts/directus-db-restore.sh`
- runtime rollback script `scripts/rollback-runtime-release.sh`
- destructive deploy script `scripts/deploy-stack.sh`
- immutable runtime release directories under `releases/<git-sha>/`
- runtime state under `.deploy-state/runtime-live.env`

If any of those controls are bypassed, rollback risk increases immediately.

## Frontend Rollback Flags

The storefront needs explicit rollout flags so CMS-backed rendering can be disabled without touching Directus or backend data.

Required flag contract for the frontend deployment:

- `REACT_APP_CMS_MODE=legacy|mixed|cms`
  - `legacy`: use legacy hard-coded navigation, site settings, and informational pages
  - `mixed`: keep partial rollout behavior and component-level fallback
  - `cms`: use CMS-backed navigation, site settings, and pages
- `REACT_APP_CMS_NAVIGATION_MODE=legacy|cms`
- `REACT_APP_CMS_PAGE_MODE=legacy|cms`
- `REACT_APP_CMS_SITE_SETTINGS_MODE=legacy|cms`

Recommended production behavior:

- normal rollout target: `REACT_APP_CMS_MODE=cms`
- emergency storefront rollback: set `REACT_APP_CMS_MODE=legacy`
- partial rollback: keep `REACT_APP_CMS_MODE=mixed` and switch the granular flags individually

Important note:

- these flags are part of the required rollback contract for the frontend deployment
- they are not owned by this backend repo and must be wired in the storefront deployment before production cutover

The current storefront already has per-page and per-navigation fallback UI, but that is not the same as an operator-controlled production rollback switch.

## Content Rollback Path

### Minor editorial error

Use this when one or a few records are wrong but the schema and deploy are healthy.

1. Fix the content directly in Directus.
2. Invalidate the backend CMS cache:

   ```bash
   curl -X POST https://<backend-host>/admin/content/cache/invalidate \
     -H 'Content-Type: application/json' \
     -d '{"scope":"all"}'
   ```

3. Verify the affected `/content/*` endpoint and storefront page.

### Seed-managed content rollback

Use this when the bad content came from committed seed files such as legal/about/FAQ/navigation content.

1. Identify the last known good git ref.
2. On the target VM, check out that ref.
3. Rerun the importer from that ref:

   ```bash
   cd <deploy-path>
   git fetch origin
   git checkout <known-good-ref>
   git pull --ff-only origin <known-good-ref>
   docker compose --env-file .env -f docker-compose.prod.yml exec -T \
     -e DIRECTUS_BASE_URL=http://127.0.0.1:8055 \
     directus node /opt/directus-deploy/scripts/directus-content-import.js --prune
   ```

4. Invalidate the backend CMS cache.
5. Verify the affected pages.

This is the preferred rollback path for committed seed content because it avoids a full database restore.

### Broad content corruption

Use this when content damage is widespread or the exact changed records are not trustworthy.

1. Select the known-good Directus DB backup from `backups/directus/`.
2. Confirm the object storage bucket state is still compatible with that point in time.
3. Restore the backup:

   ```bash
   cd <deploy-path>
   bash ./scripts/directus-db-restore.sh \
     --env-file .env \
     --compose-file docker-compose.prod.yml \
     --backup-file backups/directus/directus-<timestamp>.sql.gz
   ```

4. Reapply the matching application ref if needed.
5. Invalidate the backend CMS cache.
6. Run health checks.

## Schema Rollback Path

Schema rollback should follow the git-tracked snapshot, not ad hoc Studio edits.

Preferred path:

1. Identify the last known good git ref.
2. Determine whether the bad schema change was destructive.
   Destructive examples: dropped field, renamed field, relation rewrite, incompatible status/workflow change.
3. If the change was destructive, restore the matching pre-deploy Directus DB backup first.
4. Check out the last known good git ref on the target VM or trigger the destructive/manual workflow at that ref.
5. Redeploy destructively:

   ```bash
   cd <deploy-path>
   git fetch origin
   git checkout <known-good-ref>
   git pull --ff-only origin <known-good-ref>
   bash ./scripts/deploy-stack.sh --env-file .env --compose-file docker-compose.prod.yml
   ```

Why this is the preferred path:

- it restores the previous `schema.snapshot.json`
- it restores the previous seed/import logic
- it restores the previous deploy/runtime code in one step

Do not rely on “forward-fixing” a broken schema in production unless the blast radius is clearly smaller than a controlled rollback.

## Runtime Rollback Path

Use this when the problem is the Directus image version or the deployed application ref rather than the content model itself.

For runtime-safe application changes, prefer the recorded runtime release rollback over git checkout surgery. That is the smallest safe rollback unit in the new pipeline.

Important boundary for the current single-VM production bring-up:

- immediately after the first manual bootstrap of the rewritten pipeline, there is no previous blue-green release to roll back to yet
- use the legacy nginx include-file fallback from [single-vm-production-bringup.md](./single-vm-production-bringup.md) if that first bootstrap must be undone
- `scripts/rollback-runtime-release.sh` and `.github/workflows/rollback-production.yml` become useful only after at least one later successful runtime-safe deploy records a previous live release

### Directus version rollback

1. Revert `DIRECTUS_VERSION` in the server `.env` to the previous pinned tag.
2. Redeploy through the destructive/manual workflow:

   ```bash
   cd <deploy-path>
   bash ./scripts/deploy-stack.sh --env-file .env --compose-file docker-compose.prod.yml
   ```

3. If the runtime downgrade is not schema-compatible, restore the matching Directus DB backup first.

### Full application ref rollback

For runtime-safe production releases:

```bash
cd <deploy-path>
bash ./scripts/rollback-runtime-release.sh --env-file .env --compose-file docker-compose.prod.yml
```

That swaps nginx back to the recorded previous runtime slot, rechecks public health, and leaves the immutable release directories intact for later forensics.

For destructive/schema-affecting releases, use the same git-ref rollback described in the schema section.

## Post-Rollback Verification

After any rollback:

1. Run:

   ```bash
   cd <deploy-path>
   bash ./scripts/check-stack-health.sh --env-file .env --compose-file docker-compose.prod.yml
   ```

2. Verify:
   - Directus `/server/health`
   - backend `/health/redis`
   - backend `/content/navigation?placement=header`
   - backend `/content/pages/delivery`
3. Verify the storefront path selected by the frontend flags.
4. Record the rollback type, git ref, backup file, and cache invalidation performed.

## Decision Rule

Use this order unless there is a clear reason not to:

1. frontend path rollback
2. seed/content rollback
3. schema rollback
4. DB restore plus schema/runtime rollback

That order minimizes data loss and keeps the rollback surface as small as possible.
