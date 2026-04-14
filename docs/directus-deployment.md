# Directus Deployment And CI/CD

This repo now treats Directus as part of the deployed application stack instead of a separate manual service.

Operational incident handling is documented in [directus-operations-runbook.md](./directus-operations-runbook.md).
Rollback decision-making is documented in [directus-rollback-strategy.md](./directus-rollback-strategy.md).
Go-live sequencing is documented in [directus-production-cutover.md](./directus-production-cutover.md).

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

That script performs these steps:

1. Start `postgres` and `redis`
2. Run `scripts/directus-db-backup.sh`
3. Run `scripts/directus-db-init.sh`
4. Pull the pinned Directus image tag
5. Start or update `api` and `directus`
6. Apply the committed Directus schema snapshot from `directus/schema/schema.snapshot.json`
7. Run `scripts/check-stack-health.sh`

This keeps Directus core upgrades, schema drift control, and app deployment on one path.

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
- `DIRECTUS_AUTH_KEYCLOAK_CLIENT_ID`
- `DIRECTUS_AUTH_KEYCLOAK_CLIENT_SECRET`
- `DIRECTUS_AUTH_KEYCLOAK_ISSUER_URL`
- `DIRECTUS_AUTH_KEYCLOAK_ROLE_MAPPING`
- `DIRECTUS_STORAGE_S3_KEY`
- `DIRECTUS_STORAGE_S3_SECRET`
- `DIRECTUS_STORAGE_S3_BUCKET`
- `DIRECTUS_STORAGE_S3_REGION`
- `DIRECTUS_STORAGE_S3_ENDPOINT`

The full environment contract remains in [directus-environment.md](./directus-environment.md).

## First Deploy Checklist

Before the first staging or production Directus deploy:

1. Add the Directus variables to the target server `.env`
2. Create the dedicated Directus S3 bucket and credentials
3. Create the Directus Keycloak client and callback URL
4. Ensure the GitHub Environment secrets point at the correct VM and deploy path
5. Confirm the target VM can expose Directus on port `8055` or place it behind nginx

After the first container deploy:

1. Verify `https://<directus-host>/server/health`
2. Verify Keycloak SSO login
3. Run the initial content import if the environment is empty
4. Verify the backend `/content/*` facade against the deployed Directus instance

## Rollback Notes

- Roll back application code by redeploying the previous git ref.
- Roll back Directus runtime by reverting `DIRECTUS_VERSION` to the previous pinned tag and redeploying.
- Roll back CMS database content/schema by restoring a `directus-*.sql.gz` dump and then redeploying the matching code/schema snapshot.

Do not restore the Directus database without also validating the corresponding object storage state for uploaded assets.

For the operator-facing incident steps, use [directus-operations-runbook.md](./directus-operations-runbook.md).
For rollback scope selection, use [directus-rollback-strategy.md](./directus-rollback-strategy.md).
