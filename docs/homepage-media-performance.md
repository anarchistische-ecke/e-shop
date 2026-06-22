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
- Dynamic homepage, catalogue, category, search, product and sitemap responses: preserve origin `no-store`.
- Published `/content/*` responses: preserve backend `no-store` so a verified CMS publication is immediately visible.
- Query strings must participate in the CDN cache key. Do not enable `ignore query string`.

Do not apply a blanket CDN `max-age=600` rule to all storefront responses; it weakens immutable asset caching and hides HTML/content TTL intent.

## HTML And Content Purge

Apply the storefront CDN policy and purge dynamic paths with:

```bash
STOREFRONT_CDN_RESOURCE_ID=<resource-id> \
YC_BIN="$HOME/yandex-cloud/bin/yc" \
./scripts/configure-storefront-cdn-cache.sh
```

This preserves origin cache headers, removes the browser TTL override, restores query-string-aware cache keys, and purges the known dynamic routes. Keep derivative image URLs immutable and content-addressed so image derivatives do not need purging.

After deployment, run the consistency smoke check:

```bash
PRODUCT_ID=363d1e3a-e0c8-4996-ad7c-d966849ec986 \
node scripts/storefront-content-consistency-check.mjs
```

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

To repair derivatives for one product without scanning the complete catalogue, set
`PRODUCT_ID`:

```bash
PRODUCT_ID=<product-uuid> \
API_BASE=https://api.yug-postel.ru \
YANDEX_STORAGE_BUCKET=<bucket> \
YANDEX_STORAGE_KEY=<key> \
YANDEX_STORAGE_SECRET=<secret> \
MEDIA_DERIVATIVES_BUCKET=<bucket> \
npm run backfill
```

The script skips existing derivative objects, so it is safe to rerun after interrupted uploads.
