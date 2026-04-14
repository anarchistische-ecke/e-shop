# Directus Production Cutover Plan

This checklist is the production go-live sequence for switching storefront CMS content to Directus with the lowest reasonable blast radius.

Use it together with:

- [directus-deployment.md](./directus-deployment.md)
- [directus-operations-runbook.md](./directus-operations-runbook.md)
- [directus-rollback-strategy.md](./directus-rollback-strategy.md)
- [directus-content-migration.md](./directus-content-migration.md)

## Preconditions

Do not start production cutover until all of these are true:

- staging has already been deployed from the same git ref
- staging Directus login, schema apply, and content import are verified
- staging storefront smoke tests pass
- production server `.env` contains the required Directus, Keycloak, S3, and health-check variables
- production Directus bucket and credentials already exist
- production Keycloak client and redirect URL already exist
- production frontend deployment supports the rollback flags from [directus-rollback-strategy.md](./directus-rollback-strategy.md)
- a named operator owns the cutover and rollback decision during the window

## Cutover Modes

The intended storefront rollout order is:

1. `legacy`
   Storefront still serves legacy hard-coded navigation/site-settings/pages.
2. `mixed`
   CMS stack is live, but rollout can be enabled per surface.
3. `cms`
   CMS-backed navigation, site settings, and informational pages are fully enabled.

Recommended frontend flag progression:

- start with `REACT_APP_CMS_MODE=legacy`
- move to `REACT_APP_CMS_MODE=mixed`
- then switch granular flags:
  - `REACT_APP_CMS_NAVIGATION_MODE=cms`
  - `REACT_APP_CMS_SITE_SETTINGS_MODE=cms`
  - `REACT_APP_CMS_PAGE_MODE=cms`
- finish with `REACT_APP_CMS_MODE=cms`

## T-1 Preparation

1. Freeze non-essential CMS schema and seed changes until cutover completes.
2. Identify the exact git ref to deploy to production.
3. Confirm the last known good production git ref for rollback.
4. Confirm current production frontend config can be switched back to `legacy` quickly.
5. Verify the latest `backups/directus/` retention policy and object-storage recovery ownership.
6. Notify stakeholders of the cutover window and expected validation period.

## Cutover Checklist

### Phase 1: Deploy CMS stack without serving it publicly

1. Keep the production storefront in `legacy` mode.
2. Deploy the target git ref to production:

   ```bash
   cd <deploy-path>
   git fetch origin
   git checkout <target-ref>
   git pull --ff-only origin <target-ref>
   bash ./scripts/deploy-stack.sh --env-file .env --compose-file docker-compose.prod.yml
   ```

3. Verify:
   - Directus `/server/health`
   - backend `/health/redis`
   - backend `/content/navigation?placement=header`
   - backend `/content/pages/delivery`
4. Verify Directus SSO login in production.

Go/no-go rule:

- do not continue if Directus deploy, schema apply, or health checks fail

### Phase 2: Import baseline content

1. Run the committed baseline content import inside the Directus container:

   ```bash
   cd <deploy-path>
   docker compose --env-file .env -f docker-compose.prod.yml exec -T \
     -e DIRECTUS_BASE_URL=http://127.0.0.1:8055 \
     directus node /opt/directus-deploy/scripts/directus-content-import.js --prune
   ```

2. Invalidate backend CMS cache.
3. Verify in Directus Studio:
   - `site_settings` exists and is published
   - `header` and `footer` navigation groups exist
   - core informational pages exist
   - legal documents exist

Go/no-go rule:

- do not enable any frontend CMS path if baseline content import is incomplete

### Phase 3: Validate backend and smoke paths before flag enable

1. Verify backend content endpoints against production:
   - `/content/site-settings`
   - `/content/navigation?placement=header`
   - `/content/navigation?placement=footer`
   - `/content/pages/delivery`
2. Verify manual smoke in the storefront while still in `legacy` mode:
   - homepage loads
   - header navigation works
   - footer links work
   - delivery/payment/bonuses/production/about pages load
3. Run the existing automated CMS smoke test path against the target environment if available.
   This refers to the Playwright CMS smoke suite already added in the frontend repo.

Go/no-go rule:

- if backend facade responses or storefront smoke checks fail, stop here and use the rollback plan

### Phase 4: Enable partial rollout

1. Change the frontend deployment to `REACT_APP_CMS_MODE=mixed`.
2. Enable one CMS surface at a time in this order:
   - `REACT_APP_CMS_SITE_SETTINGS_MODE=cms`
   - `REACT_APP_CMS_NAVIGATION_MODE=cms`
   - `REACT_APP_CMS_PAGE_MODE=cms`
3. After each change:
   - redeploy frontend
   - verify the affected UI
   - verify backend `/content/*` responses
   - watch the `ops-health-check` workflow and app logs

Recommended hold time:

- wait at least 10 to 15 minutes after each surface enable so the scheduled health monitor runs at least once

Rollback trigger:

- if one surface misbehaves, return only that surface to `legacy` while keeping the rest unchanged

### Phase 5: Full enable

1. Once all three surface-level flags are stable, set `REACT_APP_CMS_MODE=cms`.
2. Redeploy the frontend.
3. Re-run storefront smoke validation.
4. Keep heightened monitoring during the agreed observation window.

Recommended observation window:

- 30 to 60 minutes after full enable

## Validation Checklist

Minimum post-enable validation:

- Directus `/server/health` is healthy
- backend `/health/redis` is healthy
- backend CMS endpoints return published data
- header navigation reflects Directus content
- footer navigation reflects Directus content
- at least one CMS informational page renders correctly
- legal content loads
- images/assets resolve from Directus storage
- Directus admin login still works for editors

## Monitoring During Cutover

Watch these during and after enable:

- latest `ops-health-check` workflow run
- Directus container logs
- API container logs
- storefront smoke test result
- backend `/content/*` latency and error rate if available in your external monitoring

## Rollback Triggers

Rollback immediately if any of these happen:

- Directus health check fails after deploy
- backend `/content/*` returns missing or malformed production content
- header/footer navigation breaks after CMS enable
- CMS-managed informational pages render blank or broken layouts
- editor login is broken in production
- asset URLs fail for newly CMS-managed pages

## Fast Rollback Path

Use the smallest rollback first:

1. Set frontend flags back to `REACT_APP_CMS_MODE=legacy`
2. Redeploy frontend
3. If the issue is backend/content/schema, follow [directus-rollback-strategy.md](./directus-rollback-strategy.md)

This keeps storefront recovery faster than restoring Directus by default.

## Cutover Complete

The cutover is complete only when:

- frontend is running in `cms` mode
- smoke validation is green
- observation window passes without incident
- rollback ref and backup file for the cutover are recorded in the incident/change log
