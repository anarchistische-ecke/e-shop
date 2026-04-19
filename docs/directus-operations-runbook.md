# Directus Operations Runbook

This runbook is the operational reference for production CMS incidents in the current single-VM setup.

If you later add a separate staging target, the same procedures also apply there.

Use it together with [directus-deployment.md](./directus-deployment.md).
For rollback scope selection and frontend flag strategy, use [directus-rollback-strategy.md](./directus-rollback-strategy.md).
For planned go-live sequencing, use [directus-production-cutover.md](./directus-production-cutover.md).
For metrics, alerts, and structured log search, use [directus-observability.md](./directus-observability.md).
For the non-destructive backup-restore rehearsal, use [directus-restore-drill.md](./directus-restore-drill.md).

## Health Checks

Primary health endpoints:

- backend: `GET /health/redis`
- Directus: `GET /server/health`

Operational health script:

- `scripts/check-stack-health.sh`

Default checks on the VM:

- current live API slot from `.deploy-state/runtime-live.env`
- current live Directus slot from `.deploy-state/runtime-live.env`
- public API health from `PUBLIC_API_HEALTHCHECK_URL`
- public Directus health from `${DIRECTUS_PUBLIC_URL}/server/health` or `PUBLIC_DIRECTUS_HEALTHCHECK_URL`
- public backend CMS facade health from `PUBLIC_CONTENT_HEALTHCHECK_URL`

Optional server-side overrides in `.env`:

- `API_HEALTHCHECK_URL`
- `DIRECTUS_HEALTHCHECK_URL`
- `CONTENT_HEALTHCHECK_URL`
- `PUBLIC_API_HEALTHCHECK_URL`
- `PUBLIC_DIRECTUS_HEALTHCHECK_URL`
- `PUBLIC_CONTENT_HEALTHCHECK_URL`

`CONTENT_HEALTHCHECK_URL` is optional and is useful when you want the health probe to verify the backend CMS facade too, for example `https://<backend-host>/content/navigation?placement=footer`.

## Monitoring

GitHub Actions workflow:

- `.github/workflows/ops-health-check.yml`

Current behavior:

- every 15 minutes, production health checks run over SSH
- operators can also run the same workflow manually for `production`
- the `staging` option should stay unused until a real isolated staging target exists

The workflow calls `scripts/check-stack-health.sh` on the target VM. A failure means the environment should be treated as degraded until someone inspects the host.

## Quick Triage

1. Check the latest `ops-health-check` workflow run in GitHub Actions.
2. SSH to the target VM and rerun:

   ```bash
   cd <deploy-path>
   bash ./scripts/check-stack-health.sh --env-file .env --compose-file docker-compose.prod.yml --verify-runtime-state
   ```

3. Inspect running containers:

   ```bash
   cat .deploy-state/runtime-live.env
   docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
   ls -1t .deploy-state/logs | head
   ```

4. If Directus is down but PostgreSQL is healthy, restart Directus first.
5. If Directus is healthy but CMS pages still fail, check backend logs and the `/content/*` facade.

## Restart Procedure

Restart only Directus:

```bash
cd <deploy-path>
current_slot="$(awk -F= '/^CURRENT_LIVE_SLOT=/{print $2}' .deploy-state/runtime-live.env)"
current_release_dir="$(awk -F= '/^CURRENT_LIVE_RELEASE_DIR=/{print $2}' .deploy-state/runtime-live.env)"
current_project="$(awk -F= '/^CURRENT_LIVE_PROJECT=/{print $2}' .deploy-state/runtime-live.env)"
docker compose --project-name "$current_project" --env-file .env -f "$current_release_dir/docker-compose.runtime-slot.yml" restart directus
bash ./scripts/check-stack-health.sh --env-file .env --compose-file docker-compose.prod.yml --verify-runtime-state
```

Restart API and Directus together:

```bash
cd <deploy-path>
current_release_dir="$(awk -F= '/^CURRENT_LIVE_RELEASE_DIR=/{print $2}' .deploy-state/runtime-live.env)"
current_project="$(awk -F= '/^CURRENT_LIVE_PROJECT=/{print $2}' .deploy-state/runtime-live.env)"
docker compose --project-name "$current_project" --env-file .env -f "$current_release_dir/docker-compose.runtime-slot.yml" restart api directus
bash ./scripts/check-stack-health.sh --env-file .env --compose-file docker-compose.prod.yml --verify-runtime-state
```

If the restart does not recover the service, use the rollback or restore paths below instead of looping restarts.

## Backup Restore Procedure

Use this only when the Directus database is the problem and you have a known-good backup from `backups/directus/`.

1. Identify the backup to restore.
2. Confirm object storage state is compatible with that backup.
3. On the VM, restore the database:

   ```bash
   cd <deploy-path>
   bash ./scripts/directus-db-restore.sh \
     --env-file .env \
     --compose-file docker-compose.prod.yml \
     --backup-file backups/directus/directus-<timestamp>.sql.gz
   ```

4. Reapply the committed schema snapshot if needed:

   ```bash
   docker compose --env-file .env -f docker-compose.prod.yml exec -T \
     -e DIRECTUS_BASE_URL=http://127.0.0.1:8055 \
     directus node /opt/directus-deploy/scripts/directus-schema.js apply
   ```

5. Run health checks:

   ```bash
   bash ./scripts/check-stack-health.sh --env-file .env --compose-file docker-compose.prod.yml
   ```

Do not restore the Directus database without validating the corresponding bucket state for uploaded files.

## Restore Drill

Run the restore drill:

- before the first production cutover
- after material backup/restore script changes
- after major Directus runtime/schema changes
- at least quarterly while Directus is production-critical

Preferred non-destructive rehearsal:

```bash
cd <deploy-path>
bash ./scripts/directus-db-restore-drill.sh \
  --env-file .env \
  --backup-file backups/directus/directus-<timestamp>.sql.gz
```

This restores the chosen backup into a disposable Postgres container, validates core Directus tables plus the governed CMS tables, and then destroys the container. Record the backup file, date, operator, and result in the incident/change log.

## Rollback Procedure

Use rollback when the outage is caused by a bad deploy rather than broken content.

### Application rollback

Use the recorded previous live runtime release:

```bash
cd <deploy-path>
bash ./scripts/rollback-runtime-release.sh --env-file .env --compose-file docker-compose.prod.yml
```

If the recorded previous release is not the one you want, use the manual GitHub workflow `.github/workflows/rollback-production.yml` or select the release id explicitly when the state file still records it.

Important first-bootstrap boundary:

- immediately after the first manual blue-green bootstrap, there is no previous runtime release yet
- `rollback-production.yml` only becomes useful after a later successful runtime-safe deploy records `previous-live`
- for that first bootstrap only, use the legacy include-file fallback described in [single-vm-production-bringup.md](./single-vm-production-bringup.md) if you must return to the old `8080/8055` stack

### Directus runtime rollback

If the problem is a Directus upgrade, revert `DIRECTUS_VERSION` in the server `.env` to the previous pinned tag and rerun the destructive/manual deploy path:

```bash
cd <deploy-path>
bash ./scripts/deploy-stack.sh --env-file .env --compose-file docker-compose.prod.yml
```

If the rollback crosses schema compatibility boundaries, restore the matching database backup first.

## Incident Notes

Capture these after the incident:

- failing health endpoint
- affected environment
- deploy ref and Directus version
- whether restart, rollback, or restore was required
- backup file used, if any
- whether object storage also needed manual recovery
