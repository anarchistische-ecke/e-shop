# Directus Content Cache Strategy

This backend uses Redis to cache published CMS responses fetched from Directus. The cache lives in the backend layer, not in the browser, so the storefront still talks only to the backend facade.

This strategy is separate from Directus's own optional data cache. If Directus itself later needs more protection under production load, enable its built-in Redis-backed cache independently during deployment.

## Scope

Cached backend endpoints:

- `GET /content/site-settings`
- `GET /content/navigation`
- `GET /content/navigation?placement=<placement>`
- `GET /content/pages/{slug}`

The backend caches only the final facade response payloads returned from these endpoints.

Preview endpoints under `GET /content/preview/*` are intentionally not cached. Draft review should show the freshest non-archived content, and those routes are already protected by Keycloak-backed editor/admin access.

## Default TTL

- Default TTL: `PT5M`
- Config variable: `DIRECTUS_CACHE_TTL`
- Set `DIRECTUS_CACHE_TTL=PT0S` to disable CMS response caching

Five minutes is the current default because the CMS content is editorial, public, and relatively slow-moving compared with commerce data. This reduces repeated Directus reads without keeping stale content around for long.

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
- On cache miss, the backend fetches from Directus, returns the response, and writes it back to Redis with the configured TTL.
- If Redis is unavailable during a read or write, the backend logs a warning and falls back to Directus instead of failing the request.
- Public content still respects the published-only Directus filters already implemented in the backend client.

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
- invalidation errors should surface to the caller, because this is an operational action rather than a public read path

## Operational Guidance

- Run a targeted invalidation after editorial changes when you need immediate storefront freshness.
- Run `scope=all` after schema or seed changes that could affect multiple pages or navigation structures.
- If a page slug changes, invalidate the full cache or invalidate both the old and new page keys.
- If Directus becomes the bottleneck later, consider enabling Directus `CACHE_ENABLED=true`, `CACHE_STORE=redis`, and shared `REDIS` settings in the Directus deployment as a second caching layer.
