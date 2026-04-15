# Directus Content Cache Strategy

This backend uses Redis to cache published CMS responses fetched from Directus. The cache lives in the backend layer, not in the browser, so the storefront still talks only to the backend facade.

Published CMS reads now have three cache layers available:

- backend Redis cache for the normalized `/content/*` facade payloads
- browser/intermediary cache headers on public `/content/*` responses
- optional Directus output caching backed by Redis for the upstream Directus API itself

This strategy is separate from Directus's own optional data cache. If Directus itself later needs more protection under production load, enable its built-in Redis-backed cache independently during deployment.

## Scope

Cached backend endpoints:

- `GET /content/site-settings`
- `GET /content/navigation`
- `GET /content/navigation?placement=<placement>`
- `GET /content/pages/{slug}`

The backend caches only the final facade response payloads returned from these endpoints.

Preview endpoints under `GET /content/preview/*` are intentionally not cached. Draft review should show the freshest non-archived content, and those routes are already protected by Keycloak-backed editor/admin access.

## Default TTLs

- Active backend cache TTL: `PT5M`
- Config variable: `DIRECTUS_CACHE_TTL`
- Set `DIRECTUS_CACHE_TTL=PT0S` to disable the primary backend CMS response cache

- Stale fallback TTL: `PT1H`
- Config variable: `DIRECTUS_CACHE_STALE_TTL`
- If Directus times out or returns an upstream error after the primary cache entry expires, the backend can still serve the last good published payload from this stale tier

- Public response `Cache-Control` max-age: `PT1M`
- Config variable: `DIRECTUS_RESPONSE_CACHE_MAX_AGE`

- Public response `stale-while-revalidate`: `PT5M`
- Config variable: `DIRECTUS_RESPONSE_CACHE_STALE_WHILE_REVALIDATE`

- Public response `stale-if-error`: `PT1H`
- Config variable: `DIRECTUS_RESPONSE_CACHE_STALE_IF_ERROR`

Five minutes is the current default active TTL because the CMS content is editorial, public, and relatively slow-moving compared with commerce data. The one-hour stale tier gives the storefront a bounded fallback if Directus is temporarily slow or unavailable. The one-minute browser/intermediary max-age keeps SPA route changes fast without making cache invalidation unreasonably slow to propagate.

## Cache Keys

Redis key prefix:

- `cms:content`
- Config variable: `DIRECTUS_CACHE_KEY_PREFIX`

Current key shapes:

- `cms:content:site-settings`
- `cms:content:navigation:all`
- `cms:content:navigation:<placement>`
- `cms:content:page:<slug>`

Examples:

- `cms:content:navigation:footer`
- `cms:content:page:delivery`

## Read Behavior

- On cache hit, the backend returns the cached JSON payload.
- On cache miss, the backend fetches from Directus, returns the response, and writes both the active cache entry and the stale fallback copy to Redis.
- If Directus fails after the active cache entry expires, the backend serves the stale fallback copy for published content when available.
- If Redis is unavailable during a read or write, the backend logs a warning and falls back to Directus instead of failing the request.
- Public content still respects the published-only Directus filters already implemented in the backend client.

Preview endpoints still bypass these caches and return `Cache-Control: private, no-store, max-age=0`.

Public published endpoints return `Cache-Control` based on the response cache settings above, for example:

```http
Cache-Control: public, max-age=60, stale-while-revalidate=300, stale-if-error=3600
```

## Manual Invalidation

Admin-only endpoint:

- `POST /admin/content/cache/invalidate`

Supported request bodies:

Clear everything:

```json
{
  "scope": "all"
}
```

Clear only site settings:

```json
{
  "scope": "site_settings"
}
```

Clear navigation:

```json
{
  "scope": "navigation"
}
```

Clear one placement and the aggregate navigation cache:

```json
{
  "scope": "navigation",
  "placement": "footer"
}
```

Clear one page:

```json
{
  "scope": "page",
  "slug": "delivery"
}
```

Notes:

- `scope=page` requires `slug`
- `scope=navigation` with `placement` clears both `navigation:all` and the specific placement key
- targeted invalidation also clears the matching stale fallback keys
- invalidation errors should surface to the caller, because this is an operational action rather than a public read path

## Operational Guidance

- Run a targeted invalidation after editorial changes when you need immediate storefront freshness.
- Run `scope=all` after schema or seed changes that could affect multiple pages or navigation structures.
- If a page slug changes, invalidate the full cache or invalidate both the old and new page keys.

## Directus Output Cache

Directus itself now supports an optional Redis-backed output cache in the compose files.

Production defaults:

- `DIRECTUS_DATA_CACHE_ENABLED=true`
- `DIRECTUS_DATA_CACHE_TTL=5m`
- `DIRECTUS_DATA_CACHE_AUTO_PURGE=true`
- `DIRECTUS_DATA_CACHE_STORE=redis`
- `DIRECTUS_DATA_CACHE_STATUS_HEADER=X-Directus-Cache`
- `DIRECTUS_REDIS_URL=redis://redis:6379`

Local default:

- `DIRECTUS_DATA_CACHE_ENABLED=false`

These variables map to Directus `CACHE_ENABLED`, `CACHE_TTL`, `CACHE_AUTO_PURGE`, `CACHE_STORE`, `CACHE_STATUS_HEADER`, and `REDIS`.

## Assets And CDN

Asset delivery uses `DIRECTUS_PUBLIC_URL/assets/{id}` as the canonical storefront path.

- Put a CDN or reverse proxy in front of `DIRECTUS_PUBLIC_URL/assets/*` in production.
- Let the CDN respect the `Cache-Control` and `Last-Modified` headers returned by Directus for `/assets`.
- Do not point the storefront at raw object-storage URLs as the normal asset path; keep those for low-level debugging and smoke checks only.
