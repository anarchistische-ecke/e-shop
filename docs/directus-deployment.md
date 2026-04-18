# Directus Deployment And CI/CD

This repo now treats Directus as part of the deployed application stack instead of a separate manual service.

Operational incident handling is documented in [directus-operations-runbook.md](./directus-operations-runbook.md).
Rollback decision-making is documented in [directus-rollback-strategy.md](./directus-rollback-strategy.md).
Go-live sequencing is documented in [directus-production-cutover.md](./directus-production-cutover.md).
Metrics, alerts, dashboards, and structured-log search are documented in [directus-observability.md](./directus-observability.md).
Backup restore rehearsal is documented in [directus-restore-drill.md](./directus-restore-drill.md).

## Deployment Shape

- One shared Docker Compose file: `docker-compose.prod.yml`
- One shared PostgreSQL service with two databases:
  - commerce: `eshop`
  - CMS: `directus`
- One Directus runtime container pinned to `11.17.2`
- One existing Redis service reused by the backend only
- One S3-compatible object storage bucket for Directus assets

The staging and production stacks use the same compose file and the same deploy script. They differ only by:

- Git ref deployed
- API image tag deployed
- VM/host target
- server-side `.env`
- GitHub Environment secrets

## CI/CD Entry Points

Workflow: `.github/workflows/backend-ci.yml`

Current deployment rules:

- push to `add-cms` -> deploy `staging`
- push to `main` -> deploy `production`
- `workflow_dispatch` -> choose `staging` or `production` and choose the Git ref

Recommended GitHub Environments:

- `staging`
- `production`

Each environment should define the same secret names:

- `YC_VM_HOST`
- `YC_VM_USER`
- `YC_VM_SSH_KEY`
- `YC_VM_DEPLOY_PATH`

If those secrets are missing, the deploy job is skipped and only CI verification runs.

## Server Deploy Flow

The GitHub Action now runs `./scripts/deploy-stack.sh` on the target VM after `git pull`.
The API Docker image is built in GitHub Actions, pushed to GHCR, and the VM only pulls the exact image tag for the current commit.

That script performs these steps:

1. Start `postgres` and `redis`
2. Run `scripts/directus-db-backup.sh`
3. Run `scripts/directus-db-init.sh`
4. Pull the exact API image tag for the deployed commit and the pinned Directus image tag
5. Start or update `api` and `directus`
6. Apply the committed Directus schema snapshot from `directus/schema/schema.snapshot.json`
7. Run `scripts/directus-published-at-bootstrap.sh`
8. Run `scripts/check-stack-health.sh`
9. Load the committed Directus operator runtime extensions from `directus/runtime-extensions/`

This keeps Directus core upgrades, schema drift control, publish-timestamp automation, and app deployment on one path.

`scripts/directus-db-init.sh` also enforces the CMS/commerce boundary during provisioning. It creates or updates the dedicated Directus runtime role, revokes `PUBLIC` access on both databases, revokes Directus access to the commerce database/schema, and grants the commerce runtime role the expected commerce-side schema access. When the commerce runtime role differs from the PostgreSQL bootstrap/admin role, the script also provisions it as a non-superuser login role.

## Migrations

Two different migration layers matter here:

- Directus core database migrations:
  Directus handles these automatically on container start for the pinned image version.
- Project CMS schema:
  The deploy script applies the committed snapshot with `scripts/directus-schema.js apply` inside the running Directus container.

This means the runtime VM does not need a host Node install. It only needs Docker Compose, Git, and shell access.

The production compose file mounts the committed `directus/` and `scripts/` directories read-only into the Directus container so schema apply and seed-content import/rollback can run inside the container when needed.

## Backups

The deploy path now takes a pre-deploy PostgreSQL dump of the `directus` database when it already exists:

- script: `scripts/directus-db-backup.sh`
- default output directory on the server: `backups/directus/`
- format: `directus-<timestamp>.sql.gz`
- default retention: 14 days

Important scope boundary:

- the automated backup covers the Directus PostgreSQL database only
- it does not back up S3/Yandex Object Storage assets

For production, treat object storage separately:

- keep Directus assets in a dedicated bucket
- enable bucket versioning or the Yandex backup/retention policy you choose
- document restore ownership outside this repo if infra manages bucket snapshots elsewhere

## Required Server-Side `.env`

The compose file now expects these Directus deployment variables in the target server `.env`:

- `DIRECTUS_VERSION`
- `DIRECTUS_KEY`
- `DIRECTUS_SECRET`
- `DIRECTUS_ADMIN_EMAIL`
- `DIRECTUS_ADMIN_PASSWORD`
- `DIRECTUS_PUBLIC_URL`
- `DIRECTUS_SCHEMA_ADMIN_TOKEN` recommended for automation
- `DIRECTUS_DB_DATABASE`
- `DIRECTUS_DB_USER`
- `DIRECTUS_DB_PASSWORD`
- `DIRECTUS_REDIS_URL` recommended when Directus output cache is enabled
- `DIRECTUS_AUTH_KEYCLOAK_CLIENT_ID`
- `DIRECTUS_AUTH_KEYCLOAK_CLIENT_SECRET`
- `DIRECTUS_AUTH_KEYCLOAK_ISSUER_URL`
- `DIRECTUS_BRIDGE_TOKEN`
- `DIRECTUS_STOREFRONT_OPS_BACKEND_URL`
- `DIRECTUS_STORAGE_S3_KEY`
- `DIRECTUS_STORAGE_S3_SECRET`
- `DIRECTUS_STORAGE_S3_BUCKET`
- `DIRECTUS_STORAGE_S3_REGION`
- `DIRECTUS_STORAGE_S3_ENDPOINT`
- `API_IMAGE_REPOSITORY` optional override for manual deploys. The GitHub workflow injects this automatically.
- `API_IMAGE_TAG` optional override for manual deploys. The GitHub workflow injects the exact commit SHA automatically.

These role-related variables are now optional because the production bootstrap seeds the same stable Directus role IDs used locally and the compose defaults point at those IDs:

- `DIRECTUS_AUTH_KEYCLOAK_ROLE_MAPPING`
- `DIRECTUS_STOREFRONT_OPS_CATALOGUE_ROLE_IDS`
- `DIRECTUS_STOREFRONT_OPS_INVENTORY_ROLE_IDS`

Recommended cache-related variables in the same `.env`:

- `DIRECTUS_CACHE_TTL`
- `DIRECTUS_CACHE_STALE_TTL`
- `DIRECTUS_RESPONSE_CACHE_MAX_AGE`
- `DIRECTUS_RESPONSE_CACHE_STALE_WHILE_REVALIDATE`
- `DIRECTUS_RESPONSE_CACHE_STALE_IF_ERROR`
- `DIRECTUS_DATA_CACHE_ENABLED`
- `DIRECTUS_DATA_CACHE_TTL`
- `DIRECTUS_DATA_CACHE_AUTO_PURGE`
- `DIRECTUS_DATA_CACHE_STORE`
- `DIRECTUS_DATA_CACHE_STATUS_HEADER`

The database-hardening step derives the commerce runtime connection from:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

Recommended production split:

- `POSTGRES_USER=postgres_admin`
- `SPRING_DATASOURCE_USERNAME=eshop_app`

For an existing deployment that still uses one shared role for both values, change the env first and rerun `scripts/directus-db-init.sh` before redeploying the API so the `eshop_app` role and grants exist before Spring reconnects.

If you need explicit overrides for `scripts/directus-db-init.sh`, also set:

- `ESHOP_DB_DATABASE`
- `ESHOP_DB_USER`
- `ESHOP_DB_PASSWORD`

The full environment contract remains in [directus-environment.md](./directus-environment.md).

## First Deploy Checklist

Before the first staging or production Directus deploy:

1. Add the Directus variables to the target server `.env`
2. Create the dedicated Directus S3 bucket and credentials
3. Create the Directus Keycloak client and callback URL
4. Add the Directus operator bridge token. Role-id env overrides are optional now.
5. Ensure the GitHub Environment secrets point at the correct VM and deploy path
6. Confirm the target VM can expose Directus on port `8055` or place it behind nginx
7. Run one successful restore drill with `scripts/directus-db-restore-drill.sh` against a recent Directus backup and record the result

After the first container deploy:

1. Verify `https://<directus-host>/server/health`
2. Verify Keycloak SSO login
3. Run `bash ./scripts/directus-storage-smoke-test.sh --env-file .env`
4. Run `node ./scripts/directus-content-audit.js --env-file .env`
5. Run the initial content import if the environment is empty
6. Verify the backend `/content/*` facade against the deployed Directus instance
7. Confirm published `/content/*` responses return the expected `Cache-Control` header and preview routes return `private, no-store`

## Residual Infra Dependency

This repo now defines backend Redis TTLs, stale fallback behavior, browser/intermediary `Cache-Control` headers, and optional Directus Redis output caching. One material residual still remains outside the repo-owned stack:

- production should place a CDN or reverse proxy in front of `DIRECTUS_PUBLIC_URL/assets/*` and let it respect Directus asset caching headers

That rule is not implemented in this repo because the production nginx/CDN configuration is managed externally. Until that infra change exists, editorial pages still work, but asset delivery will not get the full latency and cache-hit benefit planned for storefront media.

## Rollback Notes

- Roll back application code by redeploying the previous git ref.
- Roll back Directus runtime by reverting `DIRECTUS_VERSION` to the previous pinned tag and redeploying.
- Roll back CMS database content/schema by restoring a `directus-*.sql.gz` dump and then redeploying the matching code/schema snapshot.

Do not restore the Directus database without also validating the corresponding object storage state for uploaded assets.

For the operator-facing incident steps, use [directus-operations-runbook.md](./directus-operations-runbook.md).
For rollback scope selection, use [directus-rollback-strategy.md](./directus-rollback-strategy.md).
