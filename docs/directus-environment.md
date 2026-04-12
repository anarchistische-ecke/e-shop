# Directus Environment Contract

This backend does not consume Directus yet, but these are the agreed placeholder variables for the upcoming CMS integration.

For local development, the Directus compose stack now lives at `eshop/directus/docker-compose.yml` with its own local-only env file at `eshop/directus/.env`.

The database isolation decision for production is documented in [directus-db-isolation-decision.md](./directus-db-isolation-decision.md).

## Planned Environment Variables

| Variable | Scope | Example | Notes |
| --- | --- | --- | --- |
| `DIRECTUS_BASE_URL` | backend | `http://localhost:8055` | Base URL for the Directus instance the backend should call. |
| `DIRECTUS_STATIC_TOKEN` | backend | unset in repo | Server-side Directus token, only if the backend needs authenticated CMS access. Keep it in local/prod env only. Never commit it. |

## Local Directus Compose Variables

These are used only by the local Directus stack:

| Variable | Example | Notes |
| --- | --- | --- |
| `DIRECTUS_VERSION` | `11.17.2` | Pinned current Directus image tag for local development. |
| `DIRECTUS_KEY` | `local-directus-key-please-change-if-needed` | Required by Directus. Safe as a local default, but keep production values out of git. |
| `DIRECTUS_SECRET` | `local-directus-secret-please-change-if-needed` | Required by Directus. Safe as a local default, but keep production values out of git. |
| `DIRECTUS_ADMIN_EMAIL` | `admin@example.com` | Admin user created on first boot. |
| `DIRECTUS_ADMIN_PASSWORD` | `Admin123!` | Used on first boot. Rotate locally by rebuilding volumes if needed. |
| `DIRECTUS_PUBLIC_URL` | `http://localhost:8055` | Local Studio/API URL. |
| `DIRECTUS_DB_DATABASE` | `directus` | Internal Postgres database name for the Directus stack. |
| `DIRECTUS_DB_USER` | `directus` | Internal Postgres user for the Directus stack. |
| `DIRECTUS_DB_PASSWORD` | `directus` | Internal Postgres password for the Directus stack. |
| `DIRECTUS_STORAGE_S3_KEY` | `minioadmin` | Access key used by the local S3-compatible storage service. |
| `DIRECTUS_STORAGE_S3_SECRET` | `minioadmin123` | Secret key used by the local S3-compatible storage service. |
| `DIRECTUS_STORAGE_S3_BUCKET` | `directus` | Bucket created automatically for local Directus uploads. |
| `DIRECTUS_STORAGE_S3_REGION` | `us-east-1` | Region value used by the S3 adapter. |
| `DIRECTUS_STORAGE_S3_ENDPOINT` | `http://storage:9000` | Internal endpoint used by the Directus container to reach MinIO. |
| `DIRECTUS_STORAGE_S3_FORCE_PATH_STYLE` | `true` | Required for the local MinIO endpoint. |
| `DIRECTUS_STORAGE_PUBLIC_BASE_URL` | `http://localhost:9000/directus` | Optional raw object URL base for local bucket access. |

The helper script `scripts/dev-infra-up.sh` auto-creates `keycloak/.env` and `directus/.env` from their matching `.env.example` files when they are missing.

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

## Frontend Pairing

The storefront repo uses the matching public names in its `.env.example`:

- `REACT_APP_DIRECTUS_BASE_URL`
- `REACT_APP_DIRECTUS_PUBLIC_TOKEN`

The frontend token must stay public/read-only. Do not reuse `DIRECTUS_STATIC_TOKEN` in the browser.

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
