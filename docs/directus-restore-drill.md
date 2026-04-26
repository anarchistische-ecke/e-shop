# Directus Backup Restore Drill

This document defines the non-destructive restore rehearsal for Directus backups.

Use it together with:

- [directus-operations-runbook.md](./directus-operations-runbook.md)
- [directus-deployment.md](./directus-deployment.md)
- [directus-rollback-strategy.md](./directus-rollback-strategy.md)

## Goal

Prove that a chosen Directus PostgreSQL backup can be restored and passes baseline validation before a real incident depends on it.

The drill is intentionally non-destructive. It restores the backup into a disposable Postgres container instead of touching the running staging or production stack.

## Minimum Cadence

Run this drill:

- before the first production cutover
- after material changes to backup or restore scripts
- after major Directus runtime or schema changes
- at least quarterly while Directus is production-critical

## Inputs

- one known-good backup file from `backups/directus/`
- Docker access on the machine running the drill
- the server `.env` file or another env file that defines `DIRECTUS_CMS_PUBLIC_COLLECTIONS`

## Drill Command

```bash
cd <deploy-path>
bash ./scripts/directus-db-restore-drill.sh \
  --env-file .env \
  --backup-file backups/directus/directus-<timestamp>.sql.gz
```

Optional overrides:

- `--postgres-image <image>` if you need a different disposable Postgres version
- `--timeout-seconds <seconds>` if the local machine is slow to start Docker containers

## What The Drill Verifies

The drill script:

1. starts a disposable Postgres container
2. restores the selected SQL or SQL.gz backup into it
3. verifies core Directus system tables exist:
   - `directus_users`
   - `directus_collections`
   - `directus_fields`
4. verifies each governed CMS table from `DIRECTUS_CMS_PUBLIC_COLLECTIONS` exists
5. prints restored user and collection counts
6. destroys the disposable container

It does not validate object storage contents. Bucket state still has to be reviewed separately when planning a real restore.

## Pass Criteria

The drill passes when:

- the restore command completes without SQL errors
- core Directus system tables are present
- all governed CMS tables are present
- the script exits successfully

## Failure Handling

Treat the drill as failed if:

- the backup cannot be decompressed or restored
- required Directus system tables are missing after restore
- any governed CMS table is missing
- Docker cannot start the disposable Postgres container

If the drill fails:

1. stop using that backup set as your primary recovery assumption
2. inspect the backup file and the latest backup generation logs
3. fix the backup or restore path
4. rerun the drill and record the successful result before closing the work

## Recording

Record these fields in the incident/change log or ops notes:

- drill date and operator
- environment the backup came from
- exact backup filename
- script version or git ref used for the drill
- pass/fail result
- follow-up actions if anything failed
