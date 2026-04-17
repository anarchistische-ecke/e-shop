# Directus Operations Runbook

This runbook is the operational reference for staging and production CMS incidents.

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

- `http://127.0.0.1:8080/health/redis`
- `${DIRECTUS_PUBLIC_URL}/server/health` if `DIRECTUS_PUBLIC_URL` is set in `.env`
- otherwise `http://127.0.0.1:8055/server/health`

Optional server-side overrides in `.env`:

- `API_HEALTHCHECK_URL`
- `DIRECTUS_HEALTHCHECK_URL`
- `CONTENT_HEALTHCHECK_URL`

`CONTENT_HEALTHCHECK_URL` is optional and is useful when you want the health probe to verify the backend CMS facade too, for example `https://<backend-host>/content/navigation?placement=footer`.

## Monitoring

GitHub Actions workflow:

- `.github/workflows/ops-health-check.yml`

Current behavior:

- every 15 minutes, production health checks run over SSH
- operators can also run the same workflow manually for `staging` or `production`

The workflow calls `scripts/check-stack-health.sh` on the target VM. A failure means the environment should be treated as degraded until someone inspects the host.

## Quick Triage

1. Check the latest `ops-health-check` workflow run in GitHub Actions.
2. SSH to the target VM and rerun:

   ```bash
   cd <deploy-path>
   bash ./scripts/check-stack-health.sh --env-file .env --compose-file docker-compose.prod.yml
   ```

3. Inspect running containers:

   ```bash
   docker compose --env-file .env -f docker-compose.prod.yml ps
   docker compose --env-file .env -f docker-compose.prod.yml logs --tail 200 directus api postgres
   ```

4. If Directus is down but PostgreSQL is healthy, restart Directus first.
5. If Directus is healthy but CMS pages still fail, check backend logs and the `/content/*` facade.

## Restart Procedure

Restart only Directus:

```bash
cd <deploy-path>
docker compose --env-file .env -f docker-compose.prod.yml restart directus
bash ./scripts/check-stack-health.sh --env-file .env --compose-file docker-compose.prod.yml
```

Restart API and Directus together:

```bash
cd <deploy-path>
docker compose --env-file .env -f docker-compose.prod.yml restart api directus
bash ./scripts/check-stack-health.sh --env-file .env --compose-file docker-compose.prod.yml
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

1. SSH to the VM.
2. Check out the previous known-good git ref.
3. Redeploy:

   ```bash
   cd <deploy-path>
   git fetch origin
   git checkout <known-good-ref>
   git pull --ff-only origin <known-good-ref>
   bash ./scripts/deploy-stack.sh --env-file .env --compose-file docker-compose.prod.yml
   ```

### Directus runtime rollback

If the problem is a Directus upgrade, revert `DIRECTUS_VERSION` in the server `.env` to the previous pinned tag and rerun:

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
