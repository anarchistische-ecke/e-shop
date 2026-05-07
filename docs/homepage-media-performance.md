# Homepage Media Performance

This runbook covers the storefront image path used by the compact homepage card API and SSR hints.

## Runtime Settings

- `MEDIA_DERIVATIVES_PUBLIC_BASE_URL`: public CDN origin for generated derivatives. Production default: `https://img.yug-postel.ru`.
- `MEDIA_DERIVATIVES_PATH_PREFIX`: object key prefix for derivatives. Default: `media`.
- `REACT_APP_IMAGE_CDN_BASE`: storefront preconnect origin. Use the same host as `MEDIA_DERIVATIVES_PUBLIC_BASE_URL`.

Derivative keys are deterministic and immutable:

```text
media/<original-object-key-without-extension>/w<width>.<format>
```

Example:

```text
media/products/<product-id>/<image-id>/w640.webp
```

## CDN Rules

- `img.yug-postel.ru/media/*`: cache by full path, ignore cookies, `public, max-age=31536000, immutable`.
- `yug-postel.ru/assets/*`: cache by full path, ignore cookies, `public, max-age=31536000, immutable`.
- SSR HTML: preserve origin `public, max-age=0, s-maxage=60, stale-while-revalidate=300`.
- `/content/*`: preserve backend `public, max-age=60, stale-while-revalidate=300, stale-if-error=3600`.

Do not apply a blanket CDN `max-age=600` rule to all storefront responses; it weakens immutable asset caching and hides HTML/content TTL intent.

## HTML And Content Purge

`scripts/deploy-storefront-image.sh` runs an optional post-verify CDN purge hook. Configure it in the deploy environment:

```bash
STOREFRONT_CDN_PURGE_ORIGIN=https://yug-postel.ru
STOREFRONT_CDN_PURGE_PATHS="/,/content/*"
STOREFRONT_CDN_PURGE_COMMAND='<provider-specific purge command>'
```

The command receives `STOREFRONT_CDN_PURGE_ORIGIN` and `STOREFRONT_CDN_PURGE_PATHS` in its environment. Keep derivative image URLs immutable and content-addressed so image derivatives do not need purging.

## Backfill

Install the media backfill dependencies once:

```bash
cd scripts/media-derivatives
npm install
```

Run a dry run:

```bash
API_BASE=https://api.yug-postel.ru \
YANDEX_STORAGE_BUCKET=<bucket> \
YANDEX_STORAGE_KEY=<key> \
YANDEX_STORAGE_SECRET=<secret> \
MEDIA_DERIVATIVES_BUCKET=<bucket> \
npm run backfill -- --dry-run
```

Run the upload after checking the dry-run output:

```bash
API_BASE=https://api.yug-postel.ru \
YANDEX_STORAGE_BUCKET=<bucket> \
YANDEX_STORAGE_KEY=<key> \
YANDEX_STORAGE_SECRET=<secret> \
MEDIA_DERIVATIVES_BUCKET=<bucket> \
npm run backfill
```

The script skips existing derivative objects, so it is safe to rerun after interrupted uploads.
