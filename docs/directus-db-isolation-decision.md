# ADR: Directus Database Isolation Strategy

- Status: Accepted
- Date: 2026-04-12
- Decision owner: CMS integration workstream

## Decision

Choose **Option A: a separate PostgreSQL database in the same PostgreSQL cluster/service**.

For production on the current Yandex Cloud Docker deployment, keep the existing backend database for commerce data and provision a second database dedicated to Directus:

- Commerce database: `eshop`
- CMS database: `directus`

Do **not** let Directus connect to the commerce database. Do **not** reuse the backend database user for Directus.

## Why This Option

This project already runs as a single Docker-based production stack with one PostgreSQL service in [docker-compose.prod.yml](../docker-compose.prod.yml). In that setup, a separate database in the same PostgreSQL service gives the needed safety boundary without adding a second database server, extra backups, extra failover handling, or extra Yandex Cloud infrastructure on day one.

This is the right default here because:

- Directus manages its own schema and migrations cleanly in its own database.
- Separate PostgreSQL databases are a strong enough boundary to prevent accidental edits to commerce tables when paired with separate login roles and revoked default grants.
- The operational overhead is much lower than running a second PostgreSQL instance.
- It still leaves a clean upgrade path to a separate instance later if scale, compliance, or noisy-neighbor risk justifies it.

## Rejected Option

### Option B: separate PostgreSQL instance

This would give a stronger infrastructure blast-radius boundary, but it is not justified yet for the current single-stack deployment model.

We should revisit Option B later only if one or more of these become true:

- Directus traffic or background jobs materially affect commerce database performance.
- Backup/restore policies for CMS and commerce must be fully independent at the server level.
- Security/compliance requirements demand host-level or instance-level isolation.
- The stack moves from one Docker host to a more segmented production topology anyway.

## Required Database Roles

Use three distinct role types:

- `postgres_admin`
  Only for provisioning, backups, restores, and maintenance. Never used by the app or Directus containers.
- `eshop_app`
  Backend application login role. Used only by the Spring API against the `eshop` database.
- `directus_app`
  Directus application login role. Used only by Directus against the `directus` database.

If you keep the current container bootstrap user from `POSTGRES_USER`, treat it as `postgres_admin` in practice and do not wire it into either runtime application.

## Permissions Model

### Global rules

- `PUBLIC` must not keep default cross-database access.
- `eshop_app` must have no privileges on `directus`.
- `directus_app` must have no privileges on `eshop`.
- Neither app role gets `SUPERUSER`, `CREATEDB`, `CREATEROLE`, `REPLICATION`, or `BYPASSRLS`.
- Do not install or expose cross-database access helpers for Directus, such as `postgres_fdw` or `dblink`, unless there is a separately reviewed need.

### `eshop` database

- Owner/admin: `postgres_admin`
- Runtime login: `eshop_app`
- Grants:
  - `CONNECT` and `TEMP` on database `eshop`
  - schema/table/sequence/function privileges required by the backend only inside `eshop`
- Denies:
  - no `CONNECT` to `directus`

### `directus` database

- Owner: `directus_app`
- Runtime login: `directus_app`
- Grants:
  - full ownership of database `directus`
  - full ownership/use of schema `public` inside `directus`
- Denies:
  - no `CONNECT` to `eshop`

Directus needs write access to create and migrate its own tables, so `directus_app` should own the `directus` database instead of being made read-only.

## Provisioning Outline

Run provisioning once as the PostgreSQL admin user:

```sql
CREATE ROLE eshop_app LOGIN PASSWORD 'replace-me';
CREATE ROLE directus_app LOGIN PASSWORD 'replace-me';

CREATE DATABASE eshop OWNER postgres_admin;
CREATE DATABASE directus OWNER directus_app;

REVOKE ALL ON DATABASE eshop FROM PUBLIC;
REVOKE ALL ON DATABASE directus FROM PUBLIC;

GRANT CONNECT, TEMP ON DATABASE eshop TO eshop_app;
GRANT CONNECT, TEMP ON DATABASE directus TO directus_app;
```

Then apply per-database grants:

```sql
\c eshop
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO eshop_app;
GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES, TRIGGER ON ALL TABLES IN SCHEMA public TO eshop_app;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO eshop_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES, TRIGGER ON TABLES TO eshop_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO eshop_app;

\c directus
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
ALTER SCHEMA public OWNER TO directus_app;
```

Important implementation note: if the `eshop` database already exists and is already owned/administered differently, do not destroy it for this task. Apply the privilege model in place.

## Production Deployment Implications Later

This decision does not change production yet, but it defines the future production shape:

- Keep one PostgreSQL service/container initially.
- Add a second database named `directus`.
- Add a second application user named `directus_app`.
- Keep backend env pointed at `eshop`.
- Point future Directus env at `directus`.
- Do not share credentials between backend and Directus.

## Consequences

### Positive

- Strong logical separation between CMS and commerce data.
- Lower cost and lower ops complexity than a second PostgreSQL instance.
- Directus can evolve its schema without touching backend tables.

### Tradeoffs

- Commerce and CMS still share one PostgreSQL server process, disk, and backup surface.
- Bad database-level tuning or saturation could still affect both apps.
- Full infrastructure isolation may still be needed later if traffic grows.

## References

- Directus database configuration: [Directus Docs](https://directus.io/docs/configuration/database)
- Directus Docker/self-hosting getting started: [Create a Project](https://directus.io/docs/getting-started/create-a-project)
