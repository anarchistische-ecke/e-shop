# Catalogue Media Upload Runbook

## Architecture

Catalogue uploads preserve the production two-bucket contract:

- originals: `eshop-front-s3-dbbackup`
- optimized derivatives: `yug-postel-image-derivative`
- optimized public origin: `https://img.yug-postel.ru`

The browser uploads an original directly to Object Storage under
`media-upload-pending/`. The API validates the decoded JPEG, PNG, or WebP,
copies the exact original bytes into `products/...` or `categories/...`, and
creates 27 deterministic derivatives: nine widths in AVIF, WebP, and JPEG.

The catalogue row is attached only after every derivative object is present.
The storefront continues to use `media.url` and `media.sources`, which point
to the derivative CDN. `media.originalUrl` remains storage metadata and is not
the storefront display source.

Upload and processing state is stored in Redis. A global Redis lease allows
only one API slot to optimize an image at a time. An interrupted `PROCESSING`
record is requeued only after a replacement slot acquires that lease.

## Limits And Transfer Rules

- maximum file size: 100 MiB
- maximum decoded pixels: 100,000,000
- single signed PUT: up to and including 16 MiB
- multipart upload: above 16 MiB
- multipart part size: 8 MiB
- CMS concurrency: two files and three parts per file
- supported formats: JPEG, PNG, and WebP

## Production Configuration

Required API values:

```dotenv
CATALOGUE_MEDIA_UPLOAD_ENABLED=false
CATALOGUE_MEDIA_PROCESSOR_ENABLED=true
CATALOGUE_MEDIA_MAX_FILE_SIZE=100MB
CATALOGUE_MEDIA_MAX_PIXELS=100000000
MEDIA_DERIVATIVES_BUCKET=yug-postel-image-derivative
MEDIA_DERIVATIVES_PUBLIC_BASE_URL=https://img.yug-postel.ru
CONTENT_SECURITY_POLICY_DIRECTIVES__CONNECT_SRC="'self' https://api.yug-postel.ru https://storage.yandexcloud.net https://*.storage.yandexcloud.net wss://cms.yug-postel.ru"
```

Keep the processor enabled when uploads are disabled. The feature switch
blocks new batches but deliberately lets queued work finish.

## Initial Rollout

Run these commands from the production checkout:

```bash
# Read-only inspection.
./scripts/configure-media-upload-production.sh --env-file .env --check

# Back up bucket metadata and .env, then add only the upload CORS/lifecycle rules.
./scripts/configure-media-upload-production.sh --env-file .env --apply

# Reclaim build cache only. Do not prune tagged current/previous release images.
docker builder prune -af

# Validate buckets, CORS, lifecycle, CDN, Redis, and optionally the deployed API.
./scripts/media-upload-preflight.sh \
  --env-file .env \
  --api-url https://api.yug-postel.ru
```

Deploy with `CATALOGUE_MEDIA_UPLOAD_ENABLED=false`. After the candidate slot
passes health checks, enable uploads without restarting:

```bash
./scripts/set-media-upload-feature.sh true
```

Disable new uploads immediately:

```bash
./scripts/set-media-upload-feature.sh false
```

The Redis override takes precedence over the environment default.

## Health And Triage

API health:

```bash
curl --fail https://api.yug-postel.ru/health/media
```

Pending records:

```text
GET /internal/directus/catalogue/media/uploads
GET /internal/directus/catalogue/media/uploads?targetType=PRODUCT&entityId=<uuid>
```

Statuses are `UPLOADING`, `QUEUED`, `PROCESSING`, `READY`, `FAILED`,
`ABORTED`, and `EXPIRED`. A failed optimization can be retried while the
pending or final original still exists.

## Rollback

Disable uploads first, then use the normal runtime rollback:

```bash
./scripts/set-media-upload-feature.sh false
./scripts/rollback-runtime-release.sh --env-file .env --compose-file docker-compose.prod.yml
```

The bucket changes are backward-compatible and prefix-scoped. Existing
catalogue rows and derivative objects are not rewritten. An older API ignores
Redis upload records; redeploying this version resumes queued processing.
