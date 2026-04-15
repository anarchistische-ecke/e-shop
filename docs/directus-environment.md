# Directus Environment Contract

The backend now exposes a CMS facade under `/content/*` and consumes published Directus content through the server-side Directus client module.

For local development, the Directus compose stack now lives at `eshop/directus/docker-compose.yml` with its own local-only env file at `eshop/directus/.env`.

The database isolation decision for production is documented in [directus-db-isolation-decision.md](./directus-db-isolation-decision.md).
The content governance baseline for Directus roles, policies, and public-read rules is documented in [directus-content-governance.md](./directus-content-governance.md).
The editor onboarding and Keycloak role-mapping process is documented in [directus-editor-onboarding.md](./directus-editor-onboarding.md).
The editorial workflow and approval process are documented in [directus-editorial-workflow.md](./directus-editorial-workflow.md).
The approved CMS collection design is documented in [directus-content-model.md](./directus-content-model.md).
The schema versioning workflow is documented in [directus-schema-versioning.md](./directus-schema-versioning.md).
The initial content import workflow is documented in [directus-content-migration.md](./directus-content-migration.md).
The storefront/backend integration choice is documented in [directus-integration-pattern-decision.md](./directus-integration-pattern-decision.md).
The backend CMS cache strategy is documented in [directus-content-cache.md](./directus-content-cache.md).
The staging/production Directus deploy path is documented in [directus-deployment.md](./directus-deployment.md).
The operational restart/restore/rollback procedures are documented in [directus-operations-runbook.md](./directus-operations-runbook.md).
The rollback scope and frontend flag contract are documented in [directus-rollback-strategy.md](./directus-rollback-strategy.md).
The production go-live sequence is documented in [directus-production-cutover.md](./directus-production-cutover.md).
The metrics, alerts, dashboards, and log-search contract are documented in [directus-observability.md](./directus-observability.md).

## Planned Environment Variables

| Variable | Scope | Example | Notes |
| --- | --- | --- | --- |
| `DIRECTUS_BASE_URL` | backend | `http://localhost:8055` | Base URL for the Directus instance the backend should call. |
| `DIRECTUS_PUBLIC_URL` | backend + Directus | `https://cms.example.com` | Browser-reachable Directus origin used when backend CMS payloads emit `/assets/{id}` media URLs. If `DIRECTUS_BASE_URL` is private/internal, this must still be public. |
| `DIRECTUS_STATIC_TOKEN` | backend | unset in repo | Server-side Directus token. Effectively required for preview/draft reads and for published media metadata enrichment (`/files` width/height/type lookup) unless you intentionally expose Directus file metadata publicly. Keep it in local/prod env only. Never commit it. |
| `DIRECTUS_CACHE_TTL` | backend | `PT5M` | Optional Redis TTL for CMS facade responses. Defaults to 5 minutes. Set `PT0S` to disable the cache. |
| `DIRECTUS_CACHE_STALE_TTL` | backend | `PT1H` | Optional stale published-content fallback TTL. If the active cache entry expires and Directus is unavailable, the backend can still serve the last good payload until this window expires. Set `PT0S` to disable the stale tier. |
| `DIRECTUS_CACHE_KEY_PREFIX` | backend | `cms:content` | Redis key prefix for backend CMS cache entries. |
| `DIRECTUS_RESPONSE_CACHE_MAX_AGE` | backend | `PT1M` | Public `Cache-Control: max-age` for published `/content/*` responses. This is for browser/intermediary caching, separate from the Redis TTL. |
| `DIRECTUS_RESPONSE_CACHE_STALE_WHILE_REVALIDATE` | backend | `PT5M` | Public `Cache-Control: stale-while-revalidate` for published `/content/*` responses. |
| `DIRECTUS_RESPONSE_CACHE_STALE_IF_ERROR` | backend | `PT1H` | Public `Cache-Control: stale-if-error` for published `/content/*` responses. |
| `DIRECTUS_CONNECT_TIMEOUT` | backend | `PT3S` | Optional Directus connect timeout. Defaults to 3 seconds. |
| `DIRECTUS_READ_TIMEOUT` | backend | `PT5S` | Optional Directus read timeout. Defaults to 5 seconds. |

## Local Directus Compose Variables

These are used only by the local Directus stack:

| Variable | Example | Notes |
| --- | --- | --- |
| `DIRECTUS_VERSION` | `11.17.2` | Pinned current Directus image tag for local development. |
| `DIRECTUS_KEY` | `local-directus-key-please-change-if-needed` | Required by Directus. Safe as a local default, but keep production values out of git. |
| `DIRECTUS_SECRET` | `local-directus-secret-please-change-if-needed` | Required by Directus. Safe as a local default, but keep production values out of git. |
| `DIRECTUS_ADMIN_EMAIL` | `directus-admin@example.com` | Break-glass Directus local admin created on first boot. Keep it distinct from Keycloak editor/admin users. |
| `DIRECTUS_ADMIN_PASSWORD` | `Admin123!` | Used on first boot. Rotate locally by rebuilding volumes if needed. |
| `DIRECTUS_PUBLIC_URL` | `http://localhost:8055` | Local Studio/API URL. |
| `DIRECTUS_SCHEMA_ADMIN_TOKEN` | unset in repo | Optional static token used by schema snapshot/apply/check scripts. When set, it is preferred over admin email/password login. |
| `DIRECTUS_AUTH_DISABLE_DEFAULT` | `false` | Set to `true` when you want to hide Directus email/password login and require SSO. |
| `DIRECTUS_AUTH_KEYCLOAK_CLIENT_ID` | `directus` | Local Keycloak OIDC client ID for Directus Studio login. |
| `DIRECTUS_AUTH_KEYCLOAK_CLIENT_SECRET` | `directus-local-secret` | Local Keycloak client secret for the Directus OIDC client. |
| `DIRECTUS_AUTH_KEYCLOAK_ISSUER_URL` | `http://keycloak.lvh.me:8081/realms/cozyhome/.well-known/openid-configuration` | Shared browser/container issuer URL used by local Directus SSO. |
| `DIRECTUS_AUTH_KEYCLOAK_IDENTIFIER_KEY` | `email` | Directus external identifier source for matching or creating users. |
| `DIRECTUS_AUTH_KEYCLOAK_ALLOW_PUBLIC_REGISTRATION` | `true` | Allows Directus to auto-create SSO users on first login. |
| `DIRECTUS_AUTH_KEYCLOAK_REQUIRE_VERIFIED_EMAIL` | `true` | Rejects SSO users whose Keycloak email is not verified. |
| `DIRECTUS_AUTH_KEYCLOAK_SYNC_USER_INFO` | `true` | Syncs first name, last name, and email from Keycloak on each login. |
| `DIRECTUS_AUTH_KEYCLOAK_GROUP_CLAIM_NAME` | `groups` | Claim used for Directus role mapping. |
| `DIRECTUS_ROLE_CMS_ADMIN_ID` | `4c4cc8d0-9b7f-4d56-84d2-1d64f5f10001` | Stable local Directus role id for CMS Administrator. |
| `DIRECTUS_ROLE_CMS_EDITOR_ID` | `4c4cc8d0-9b7f-4d56-84d2-1d64f5f10002` | Stable local Directus role id for CMS Editor. |
| `DIRECTUS_ROLE_CMS_PUBLISHER_ID` | `4c4cc8d0-9b7f-4d56-84d2-1d64f5f10003` | Stable local Directus role id for CMS Publisher/Reviewer. |
| `DIRECTUS_POLICY_CMS_ADMIN_ID` | `4c4cc8d0-9b7f-4d56-84d2-1d64f5f20001` | Stable local Directus policy id for CMS Administrator. |
| `DIRECTUS_POLICY_CMS_EDITOR_ID` | `4c4cc8d0-9b7f-4d56-84d2-1d64f5f20002` | Stable local Directus policy id for CMS Editor. |
| `DIRECTUS_POLICY_CMS_PUBLISHER_ID` | `4c4cc8d0-9b7f-4d56-84d2-1d64f5f20003` | Stable local Directus policy id for CMS Publisher/Reviewer. |
| `DIRECTUS_ACCESS_CMS_ADMIN_ID` | `4c4cc8d0-9b7f-4d56-84d2-1d64f5f30001` | Stable local Directus access junction id for CMS Administrator. |
| `DIRECTUS_ACCESS_CMS_EDITOR_ID` | `4c4cc8d0-9b7f-4d56-84d2-1d64f5f30002` | Stable local Directus access junction id for CMS Editor. |
| `DIRECTUS_ACCESS_CMS_PUBLISHER_ID` | `4c4cc8d0-9b7f-4d56-84d2-1d64f5f30003` | Stable local Directus access junction id for CMS Publisher/Reviewer. |
| `DIRECTUS_CMS_CONTENT_COLLECTIONS` | `site_settings,navigation,navigation_items,...` | Allowlist of CMS-managed collections that receive editor/publisher permissions. |
| `DIRECTUS_CMS_PUBLIC_COLLECTIONS` | `site_settings,navigation,navigation_items,...` | Subset of content collections that the Directus public policy may read. |
| `DIRECTUS_CMS_STATUS_FIELD` | `status` | Required publish-state field used by governance rules. |
| `DIRECTUS_DB_DATABASE` | `directus` | Internal Postgres database name for the Directus stack. |
| `DIRECTUS_DB_USER` | `directus` | Internal Postgres user for the Directus stack. |
| `DIRECTUS_DB_PASSWORD` | `directus` | Internal Postgres password for the Directus stack. |
| `DIRECTUS_DATA_CACHE_ENABLED` | `false` | Optional Directus API output cache switch for local testing. Keep disabled locally unless you want to test the extra cache layer. |
| `DIRECTUS_DATA_CACHE_TTL` | `5m` | Directus output-cache TTL when the local data cache is enabled. |
| `DIRECTUS_DATA_CACHE_AUTO_PURGE` | `true` | Automatically purges the Directus output cache after content changes. |
| `DIRECTUS_DATA_CACHE_STORE` | `redis` | Cache driver used by Directus when output caching is enabled. |
| `DIRECTUS_DATA_CACHE_STATUS_HEADER` | `X-Directus-Cache` | Optional response header name exposing Directus cache `HIT`/`MISS` status. |
| `DIRECTUS_REDIS_URL` | `redis://cache:6379` | Redis connection string used by Directus when its output cache is enabled. |
| `DIRECTUS_STORAGE_S3_KEY` | `minioadmin` | Access key used by the local S3-compatible storage service. |
| `DIRECTUS_STORAGE_S3_SECRET` | `minioadmin123` | Secret key used by the local S3-compatible storage service. |
| `DIRECTUS_STORAGE_S3_BUCKET` | `directus` | Bucket created automatically for local Directus uploads. |
| `DIRECTUS_STORAGE_S3_REGION` | `us-east-1` | Region value used by the S3 adapter. |
| `DIRECTUS_STORAGE_S3_ENDPOINT` | `http://storage:9000` | Internal endpoint used by the Directus container to reach MinIO. |
| `DIRECTUS_STORAGE_S3_FORCE_PATH_STYLE` | `true` | Required for the local MinIO endpoint. |
| `DIRECTUS_STORAGE_PUBLIC_BASE_URL` | `http://localhost:9000/directus` | Optional raw object URL base for local bucket access. |

The helper script `scripts/dev-infra-up.sh` auto-creates `keycloak/.env` and `directus/.env` from their matching `.env.example` files when they are missing.

The committed Directus schema snapshot lives at `directus/schema/schema.snapshot.json`. The helper scripts are:

- `scripts/directus-schema-snapshot.sh` to export the current Directus schema into the committed snapshot file
- `scripts/directus-schema-check.sh` to detect drift between a running instance and the committed snapshot
- `scripts/directus-schema-apply.sh` to apply the committed snapshot to a running Directus instance
- `scripts/directus-content-import.sh` to upsert the initial editorial dataset from the committed seed files
- `scripts/directus-storage-smoke-test.sh` to verify upload and asset delivery through the configured Directus storage path
- `scripts/directus-content-audit.js` to fail on missing media alt fallbacks, broken asset references, and incomplete required CMS URL pairs

The helper script `scripts/dev-infra-up.sh` applies the committed schema snapshot automatically on local startup.

The helper script `scripts/directus-sso-bootstrap.sh` recreates the local Keycloak `directus` client and seeds the stable Directus roles/policies used by SSO and governance.

The helper script `scripts/keycloak-upsert-cms-user.sh` provides a repeatable local flow for creating or updating a Keycloak-backed CMS editor, publisher, or administrator.

The helper script `scripts/directus-content-model-bootstrap.sh` now acts as a compatibility wrapper around `scripts/directus-schema-apply.sh`.

The Directus schema tooling validates the committed snapshot against `DIRECTUS_CMS_CONTENT_COLLECTIONS` before it is written, checked, or applied. If someone adds commerce collections such as catalog, order, payment, stock, or shipment tables to the snapshot, the command fails fast.

## Database Isolation Bootstrap

The production shared-PostgreSQL hardening script `scripts/directus-db-init.sh` derives the commerce runtime connection from `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD` by default.

Recommended production split:

- PostgreSQL bootstrap/admin role: `POSTGRES_USER=postgres_admin`
- Backend runtime role: `SPRING_DATASOURCE_USERNAME=eshop_app`

If you need explicit overrides for that script, set:

| Variable | Example | Notes |
| --- | --- | --- |
| `ESHOP_DB_DATABASE` | `eshop` | Optional explicit commerce database name used by `scripts/directus-db-init.sh`. Falls back to `SPRING_DATASOURCE_URL` parsing, then `POSTGRES_DB`. |
| `ESHOP_DB_USER` | `eshop_app` | Optional explicit commerce runtime database user used by `scripts/directus-db-init.sh`. Falls back to `SPRING_DATASOURCE_USERNAME`. |
| `ESHOP_DB_PASSWORD` | unset in repo | Optional explicit commerce runtime database password used by `scripts/directus-db-init.sh` when the commerce app role must be created or updated. Falls back to `SPRING_DATASOURCE_PASSWORD`. |

The phase-1 content schema expects every public CMS collection to include:

- `status` with workflow values `draft`, `in_review`, `published`, `archived`
- `published_at`

- Keycloak realm role `admin` -> Directus role `CMS Administrator`
- Keycloak realm role `manager` -> Directus role `CMS Editor`
- Keycloak realm role `publisher` -> Directus role `CMS Publisher`

For predictable onboarding, grant exactly one of those mapped realm roles to a Directus user.

Local Directus SSO uses `keycloak.lvh.me` instead of `localhost` for the issuer URL because the browser and the Directus container both need a hostname that resolves consistently.

The governance baseline assumes every public CMS collection includes a `status` field with `draft`, `in_review`, `published`, and `archived`, plus `published_at`. Public reads are filtered to `status = published`, editors can only move content between `draft` and `in_review`, and publishers are the approval gate for `published`.

The schema snapshot covers the Directus data model only. Roles, policies, permissions, SSO client wiring, and seeded users remain outside the snapshot and continue to be handled by `scripts/directus-sso-bootstrap.sh`.

Initial content is also handled separately from the schema snapshot. The committed seed files live in:

- `directus/seed/initial-content.js`
- `directus/seed/legal/*.html`

The content importer authenticates with the same admin token or admin email/password used by the schema scripts.

## Directus File Storage

The local Directus stack is configured to use an S3-compatible storage adapter instead of filesystem uploads:

- `STORAGE_LOCATIONS=s3`
- `STORAGE_S3_DRIVER=s3`
- `STORAGE_S3_*` variables for bucket, credentials, region, endpoint, and path-style access

For local development, the S3-compatible provider is MinIO inside `eshop/directus/docker-compose.yml`. The `storage-init` service creates the bucket automatically and makes it downloadable for smoke-testing.

For production, keep the same Directus storage adapter but point it at Yandex Object Storage with a dedicated Directus bucket, not the backend commerce bucket.

Recommended production values:

| Variable | Example | Notes |
| --- | --- | --- |
| `STORAGE_LOCATIONS` | `s3` | Directus should upload new files to the S3 adapter. |
| `STORAGE_S3_DRIVER` | `s3` | Directus S3-compatible driver. |
| `STORAGE_S3_KEY` | unset in repo | Static access key for the Directus bucket. |
| `STORAGE_S3_SECRET` | unset in repo | Static secret key for the Directus bucket. |
| `STORAGE_S3_BUCKET` | `yug-postel-directus-assets` | Use a dedicated CMS bucket. |
| `STORAGE_S3_REGION` | `ru-central1` | Yandex Object Storage region. |
| `STORAGE_S3_ENDPOINT` | `https://storage.yandexcloud.net` | Official Yandex Object Storage S3 endpoint. |
| `STORAGE_S3_FORCE_PATH_STYLE` | deployment-specific | Keep `true` for local MinIO. Validate the required setting for Yandex Object Storage during production deployment. |

If you want raw bucket URLs in production, Yandex Object Storage documents both the base endpoint `https://storage.yandexcloud.net` and DNS-style bucket hostnames such as `https://<bucket>.storage.yandexcloud.net/<key>`. Choose the exact access style during deployment based on your bucket naming and access policy.

## Public Asset URL Strategy

Use one canonical browser-facing asset strategy for CMS content:

- backend CMS payloads should emit `DIRECTUS_PUBLIC_URL/assets/{id}` URLs
- `DIRECTUS_BASE_URL` is the backend-to-Directus upstream origin and may be private or internal
- `DIRECTUS_STORAGE_PUBLIC_BASE_URL` is optional for storage smoke checks or low-level debugging and should not be treated as the normal storefront media URL source
- place a CDN or reverse proxy in front of `DIRECTUS_PUBLIC_URL/assets/*` in production and let it respect the `Cache-Control`/`Last-Modified` headers Directus returns for `/assets`

This keeps the storefront media contract stable even when the backend reaches Directus over a different network path than the browser does.

## Directus SSO Production Variables

Use the same Directus auth model in production, but with production hostnames, a production Keycloak client, and production role IDs:

| Variable | Example | Notes |
| --- | --- | --- |
| `AUTH_PROVIDERS` | `keycloak` | Directus SSO provider list. |
| `AUTH_DISABLE_DEFAULT` | `true` | Optional. Set to `true` once the break-glass login is no longer intended for daily use. |
| `AUTH_KEYCLOAK_DRIVER` | `openid` | Directus Keycloak SSO driver. |
| `AUTH_KEYCLOAK_CLIENT_ID` | `directus` | Production Keycloak client ID. |
| `AUTH_KEYCLOAK_CLIENT_SECRET` | unset in repo | Production Keycloak client secret. |
| `AUTH_KEYCLOAK_ISSUER_URL` | `https://<keycloak-host>/realms/cozyhome/.well-known/openid-configuration` | Must match the production Keycloak realm issuer. |
| `AUTH_KEYCLOAK_IDENTIFIER_KEY` | `email` | Human-readable external identifier for Directus users. |
| `AUTH_KEYCLOAK_ALLOW_PUBLIC_REGISTRATION` | `true` | Lets Directus auto-create editor/admin users on first SSO login. |
| `AUTH_KEYCLOAK_REQUIRE_VERIFIED_EMAIL` | `true` | Keeps unverified Keycloak users out of Directus. |
| `AUTH_KEYCLOAK_SYNC_USER_INFO` | `true` | Keeps Directus user names and email aligned with Keycloak. |
| `AUTH_KEYCLOAK_GROUP_CLAIM_NAME` | `groups` | Claim used for realm-role-to-Directus-role mapping. |
| `AUTH_KEYCLOAK_ROLE_MAPPING` | `json:{"admin":"<cms-admin-role-id>","manager":"<cms-editor-role-id>","publisher":"<cms-publisher-role-id>"}` | Directus role mapping order matters because Directus assigns only one role per user. |
| `AUTH_KEYCLOAK_REDIRECT_ALLOW_LIST` | deployment-specific | Optional. Add external post-login redirect targets outside the Directus domain if you need them later. |

For production, Directus should keep a separate break-glass local admin email/password from the Keycloak editor/admin identities, just like the local setup does.

## Directus Deployment Variables

The production compose file also reads:

| Variable | Example | Notes |
| --- | --- | --- |
| `DIRECTUS_VERSION` | `11.17.2` | Keep the production Directus image pinned to the tested repo version. |
| `DIRECTUS_SCHEMA_ADMIN_TOKEN` | unset in repo | Recommended for staging/production schema automation, especially if `AUTH_DISABLE_DEFAULT=true`. |
| `DIRECTUS_DATA_CACHE_ENABLED` | `true` | Recommended in production. Enables Directus's own output cache for repeated upstream reads. |
| `DIRECTUS_DATA_CACHE_TTL` | `5m` | Directus output-cache TTL in production. |
| `DIRECTUS_DATA_CACHE_AUTO_PURGE` | `true` | Keeps Directus cache freshness acceptable after content changes. |
| `DIRECTUS_DATA_CACHE_STORE` | `redis` | Use Redis rather than Directus in-memory cache for production stability. |
| `DIRECTUS_DATA_CACHE_STATUS_HEADER` | `X-Directus-Cache` | Optional debugging header to confirm Directus cache hits/misses in staging or production. |
| `DIRECTUS_REDIS_URL` | `redis://redis:6379` | Directus Redis cache connection string for the production compose stack. |
| `API_HEALTHCHECK_URL` | deployment-specific | Optional override for `scripts/check-stack-health.sh` and remote monitoring. Defaults to `http://127.0.0.1:8080/health/redis`. |
| `DIRECTUS_HEALTHCHECK_URL` | deployment-specific | Optional override for `scripts/check-stack-health.sh`. Defaults to `${DIRECTUS_PUBLIC_URL}/server/health` when `DIRECTUS_PUBLIC_URL` is set. |
| `CONTENT_HEALTHCHECK_URL` | deployment-specific | Optional backend CMS facade probe, for example `https://<backend-host>/content/navigation?placement=footer`. |
| `APP_OBSERVABILITY_PROMETHEUS_TOKEN` | unset in repo | Optional shared token required for `GET /actuator/prometheus` via the `X-Prometheus-Token` header. |
| `DIRECTUS_SLOW_REQUEST_THRESHOLD` | `PT2S` | Duration after which a backend Directus upstream call emits `event=cms_directus_request_slow`. |

## Frontend Pairing

The storefront repo uses the matching public names in its `.env.example`:

- `REACT_APP_DIRECTUS_BASE_URL`
- `REACT_APP_DIRECTUS_PUBLIC_TOKEN`

The frontend token must stay public/read-only. Do not reuse `DIRECTUS_STATIC_TOKEN` in the browser.

Current project decision: the preferred production storefront path is the backend facade described in [directus-integration-pattern-decision.md](./directus-integration-pattern-decision.md). That means the frontend should normally consume CMS content through the backend API, not directly from Directus. Keep the frontend Directus variables only for temporary local experiments or an explicitly chosen direct-read use case later.

Required frontend rollback contract for production cutover:

- `REACT_APP_CMS_MODE=legacy|mixed|cms`
- `REACT_APP_CMS_NAVIGATION_MODE=legacy|cms`
- `REACT_APP_CMS_PAGE_MODE=legacy|cms`
- `REACT_APP_CMS_SITE_SETTINGS_MODE=legacy|cms`

Those flags are defined as rollback controls in [directus-rollback-strategy.md](./directus-rollback-strategy.md). They should live in the frontend deployment environment or runtime config layer, not inside backend secrets.

## Backend CMS Facade

The backend content module currently exposes these public read endpoints:

- `GET /content/site-settings`
- `GET /content/navigation`
- `GET /content/navigation?placement=footer`
- `GET /content/pages/{slug}`
- `GET /content/preview/site-settings`
- `GET /content/preview/navigation`
- `GET /content/preview/pages/{slug}`

Current behavior:

- Reads only published CMS content from Directus
- Preview endpoints allow non-archived content so editors and publishers can review `draft` and `in_review` content before publication
- Resolves `page_sections` and `page_section_items` server-side instead of relying on Directus alias fields
- Returns `404 CONTENT_NOT_FOUND` when a published page slug does not exist
- Keeps CMS tokens and Redis-backed caching server-side, not in the browser
- Maintains a stale published-content fallback tier in Redis so expired CMS entries can survive short Directus outages
- Emits media asset URLs from `DIRECTUS_PUBLIC_URL/assets/{id}` so browser-facing content does not depend on the backend’s internal Directus origin
- Emits `Cache-Control` on public `/content/*` responses using `DIRECTUS_RESPONSE_CACHE_*` and marks preview routes `private, no-store`
- Uses these cache keys by default: `cms:content:site-settings`, `cms:content:navigation:all`, `cms:content:navigation:<placement>`, `cms:content:page:<slug>`
- Supports admin-only manual invalidation through `POST /admin/content/cache/invalidate`
- Requires Keycloak-authenticated privileged roles for `GET /content/preview/**`; public routes remain published-only

## CORS Note

The API currently reads allowed origins from:

- dev: `api/src/main/resources/application-dev.yml`
- prod: `api/src/main/resources/application-prod.yml` via `CORS_ALLOWED_ORIGINS`

Current defaults are:

- dev: `http://localhost:3000`
- prod: `https://yug-postel.ru`

For the CMS rollout, do not change backend CORS just because Directus exists. Only add the Directus origin to `CORS_ALLOWED_ORIGINS` if browser code served from the Directus domain needs to call this backend directly.

## Secret Handling

- Do not commit real Directus tokens.
- Keep backend Directus tokens only in deployment env files or secret storage.
- If public storefront reads can be served by Directus public permissions, leave both token variables empty.
