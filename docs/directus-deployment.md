# Directus Deployment And CI/CD

This repo now treats Directus as part of the deployed application stack instead of a separate manual service.

Use these documents together:

- VM preparation: [production-vm-preparation.md](./production-vm-preparation.md)
- first single-VM bootstrap: [single-vm-production-bringup.md](./single-vm-production-bringup.md)
- operations and incidents: [directus-operations-runbook.md](./directus-operations-runbook.md)
- rollback scope: [directus-rollback-strategy.md](./directus-rollback-strategy.md)
- restore drill: [directus-restore-drill.md](./directus-restore-drill.md)
- observability: [directus-observability.md](./directus-observability.md)
- environment contract: [directus-environment.md](./directus-environment.md)

## Current Production Model

The current deployment model is:

- one Debian 11 VM
- production only
- no real staging target yet
- GitHub-hosted Actions SSH into the VM and run deploy scripts inside an existing checkout

The runtime shape on that VM is:

- one shared infrastructure compose file: `docker-compose.prod.yml`
- one runtime-slot compose file: `docker-compose.runtime-slot.yml`
- one shared PostgreSQL service with separate `eshop` and `directus` databases
- one shared Redis service
- immutable runtime releases under `releases/<git-sha>/`
- runtime state under `.deploy-state/runtime-live.env`
- blue slot on `127.0.0.1:18080` and `127.0.0.1:18055`
- green slot on `127.0.0.1:28080` and `127.0.0.1:28055`
- nginx cutover through generated upstream include files, not by mutating a live container in place

## Current CI/CD Entry Points

The workflows that matter right now are:

- `backend-ci`
  - file: `.github/workflows/backend-ci.yml`
  - runs on pull requests to `main` and manual dispatch
  - validates the change set, Directus schema snapshot, Directus runtime extensions, and Java build/test path
- `deploy-production-runtime`
  - file: `.github/workflows/deploy-production-runtime.yml`
  - runs on pushes to `main` and manual dispatch
  - builds `ghcr.io/<repo-owner>/eshop-api:<sha>` and runs `scripts/deploy-runtime-bluegreen.sh`
  - only deploys runtime-safe changes
- `deploy-production-destructive`
  - file: `.github/workflows/deploy-production-destructive.yml`
  - manual only
  - runs `scripts/deploy-stack.sh`
  - requires successful staging verification for the same SHA, so treat it as blocked until a real staging target exists
- `rollback-production`
  - file: `.github/workflows/rollback-production.yml`
  - manual only
  - runs `scripts/rollback-runtime-release.sh`
  - only rolls back to the recorded previous live blue-green release
- `ops-health-check`
  - file: `.github/workflows/ops-health-check.yml`
  - runs every 15 minutes and on manual dispatch
  - SSHes to the VM and runs `scripts/check-stack-health.sh`

Also present but intentionally unused for now:

- `deploy-staging-runtime`
  - file: `.github/workflows/deploy-staging-runtime.yml`
  - should remain unused until a separate staging target exists

## Suitability For Blue-Green On One VM

The current system is suitable for blue-green deployment on a single VM only for runtime-safe application and runtime changes.

It is not full-stack blue-green for destructive changes because:

- PostgreSQL and Redis are shared by both slots
- changes to Directus schema, Directus seed content, `docker-compose.prod.yml`, `docker-compose.runtime-slot.yml`, and `scripts/directus-*` are classified as destructive
- destructive production deploys are intentionally kept out of the automatic runtime-safe path
- the first bootstrap is manual

Treat the production workflow as:

- blue-green for runtime-safe releases
- manual for the first bootstrap
- manual and effectively unavailable for destructive releases until a real staging target exists

## Runtime-Safe Deploy Flow

The runtime-safe path is:

- workflow: `.github/workflows/deploy-production-runtime.yml`
- script: `scripts/deploy-runtime-bluegreen.sh`

That script:

1. loads `.env` as strict `KEY=value` data
2. checks host capacity, nginx include files, and Keycloak discovery
3. materializes `releases/<git-sha>/` with `git worktree`
4. starts the candidate slot from `docker-compose.runtime-slot.yml`
5. runs internal candidate health checks
6. switches the nginx upstream include files
7. runs public post-cutover health checks
8. records the new live slot in `.deploy-state/runtime-live.env`
9. retires the previous slot only after the observation window succeeds

If a failure happens after cutover, the script restores the previous nginx upstream include files automatically.

## Destructive Deploy Flow

The destructive/manual path is:

- workflow: `.github/workflows/deploy-production-destructive.yml`
- script: `scripts/deploy-stack.sh`

This path still owns:

- Directus database backup
- Directus database bootstrap and hardening
- Directus schema apply
- Directus governance bootstrap
- `published_at` bootstrap
- Directus runtime/core upgrades
- schema, seed, governance, and compose changes

The destructive flow is not part of the normal automatic single-VM runtime-safe deploy path.

## What The VM Must Already Have

The production VM must already provide:

- a Debian 11 host
- one fixed deploy checkout with a working `origin` remote
- a deploy user that can run Docker without `sudo`
- non-interactive `sudo nginx -t` and `sudo systemctl reload nginx`
- nginx API and CMS vhosts that include the generated upstream files
- a valid `<deploy-path>/.env`
- runtime directories:
  - `releases/`
  - `.deploy-state/`
  - `.deploy-state/logs/`
  - `backups/directus/`
- GitHub Environment secrets:
  - `YC_VM_HOST`
  - `YC_VM_USER`
  - `YC_VM_SSH_KEY`
  - `YC_VM_DEPLOY_PATH`
  - `GHCR_PULL_USERNAME`
  - `GHCR_PULL_TOKEN`

The authoritative preparation runbook is [production-vm-preparation.md](./production-vm-preparation.md).

## First Production Bring-Up

Important first-bootstrap boundary:

- the CI/CD rewrite commit itself is classified as `destructive`
- the first single-VM production bootstrap must therefore be performed manually on the VM
- `rollback-production.yml` becomes useful only after a later successful runtime-safe deploy records a previous live slot

Use [single-vm-production-bringup.md](./single-vm-production-bringup.md) for that one-time bootstrap.

## Migrations And Backups

Two migration layers matter:

- Directus core database migrations run automatically when the pinned Directus image starts
- project CMS schema is applied from `directus/schema/schema.snapshot.json` by `scripts/directus-schema.js apply` inside the Directus container

The destructive path also takes a Directus PostgreSQL backup through `scripts/directus-db-backup.sh` before applying schema-affecting changes.

Backup scope boundary:

- automated backup covers the Directus PostgreSQL database only
- it does not back up S3/Yandex Object Storage assets

Do not restore the Directus database without validating the matching object-storage state.

## Residual Infra Dependency

One material production dependency still lives outside this repo-owned stack:

- production should place a CDN or reverse proxy in front of `DIRECTUS_PUBLIC_URL/assets/*` and let it respect Directus asset caching headers

Until that infra piece exists, editorial pages still work, but asset delivery will not get the full cache-hit and latency benefit intended for storefront media.

## Next Documents To Use

- To prepare a fresh Debian 11 VM or adapt the existing production VM: [production-vm-preparation.md](./production-vm-preparation.md)
- To perform the first manual bootstrap on that prepared VM: [single-vm-production-bringup.md](./single-vm-production-bringup.md)
- To operate, restart, restore, or roll back after go-live: [directus-operations-runbook.md](./directus-operations-runbook.md) and [directus-rollback-strategy.md](./directus-rollback-strategy.md)
