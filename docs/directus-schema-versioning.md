# Directus Schema Versioning Workflow

This project version-controls the Directus data model with a committed schema snapshot at `directus/schema/schema.snapshot.json`.

## Team Decision

The adopted method is:

- use Directus schema snapshot/diff/apply operations over the Directus API
- commit the exported snapshot in git
- apply the committed snapshot to each environment before editors use it

This is a deliberate team choice for this repo. Directus supports promoting schema changes between environments through snapshot, diff, and apply operations, and this API-driven approach fits the existing Docker-first local setup without requiring extra container mounts or a separate local Directus CLI install.

## Source Of Truth

- Directus schema snapshot: `directus/schema/schema.snapshot.json`
- Export script: `scripts/directus-schema-snapshot.sh`
- Drift check script: `scripts/directus-schema-check.sh`
- Apply script: `scripts/directus-schema-apply.sh`

The compatibility script `scripts/directus-content-model-bootstrap.sh` now just applies the committed snapshot, so older local instructions still work.

## Workflow

1. Start the local stack and make schema changes in the local Directus Studio or through controlled scripts.
2. Export the updated schema snapshot:

   ```bash
   ./scripts/directus-schema-snapshot.sh
   ```

3. Review the git diff for `directus/schema/schema.snapshot.json`.
4. Commit the snapshot in the same PR as any code or docs that depend on the schema change.
5. Before using a target environment, apply the committed snapshot:

   ```bash
   ./scripts/directus-schema-apply.sh --env-file /path/to/directus.env
   ```

6. Optionally verify drift without changing the target environment:

   ```bash
   ./scripts/directus-schema-check.sh --env-file /path/to/directus.env
   ```

If `directus-schema-check.sh` reports changes, the running Directus instance does not match the committed schema snapshot and should not be treated as in-sync.

## Authentication

The schema scripts authenticate in this order:

- `DIRECTUS_SCHEMA_ADMIN_TOKEN`
- `DIRECTUS_ADMIN_TOKEN`
- `DIRECTUS_ADMIN_EMAIL` and `DIRECTUS_ADMIN_PASSWORD`

Local development uses the break-glass admin email/password already present in `directus/.env`. For staging or production automation, prefer a dedicated admin static token stored outside git.

## Scope

This versioning workflow covers the Directus data model:

- collections
- fields
- relations
- collection and field metadata that Directus includes in its snapshot format

It does not replace the governance/bootstrap process for:

- roles
- policies
- permissions
- Keycloak client setup
- editor/admin user provisioning

Those remain managed separately through `scripts/directus-sso-bootstrap.sh` and deployment-specific secret/config handling.

## Local Startup Behavior

`./scripts/dev-infra-up.sh` now applies the committed schema snapshot automatically after the Directus container becomes healthy, then runs the governance bootstrap.

This keeps new local environments reproducible and prevents Studio-only schema drift from becoming the hidden source of truth.

## Deployment Integration

Staging and production now reuse the same snapshot in the automated deploy path documented in [directus-deployment.md](./directus-deployment.md).

The deploy script applies the committed snapshot from inside the running Directus container, so the target VM does not need a separate host Node installation.
