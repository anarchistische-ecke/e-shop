# Directus Observability

This repo now exposes CMS-related backend metrics and structured CMS log events so production can detect Directus integration problems early.

## What Is Measured

Metrics come from two layers:

- generic backend HTTP metrics via Spring Boot Actuator
- CMS-specific Directus and cache metrics emitted by the backend content module

Main metrics:

- `http_server_requests_seconds_*`
  Backend request latency and counts, including `/content/*`
- `cms_directus_request_seconds_*`
  Upstream Directus request latency by operation and access mode
- `cms_directus_request_errors_total`
  Upstream Directus failures by operation, access mode, and error type
- `cms_cache_lookup_total`
  CMS cache hit, miss, stale-hit, stale-miss, bypass, deserialize error, and Redis read error counts
- `cms_cache_write_total`
  CMS cache write success/error counts
- `cms_cache_invalidation_deleted_keys_*`
  Deleted cache key counts per invalidation action

The backend exports Prometheus format metrics at:

- `GET /actuator/prometheus`

## Endpoint Security

Prometheus scraping can be protected with an optional shared token.

Server-side env variable:

- `APP_OBSERVABILITY_PROMETHEUS_TOKEN`

When this value is set, the scraper must send:

- header `X-Prometheus-Token: <token>`

If the token is empty, `/actuator/prometheus` is readable without authentication. Do not leave it open to the public internet; restrict it with VM firewall rules, nginx, or the Prometheus token.

## Backend Configuration

Relevant backend env/config:

- `APP_OBSERVABILITY_PROMETHEUS_TOKEN`
- `DIRECTUS_SLOW_REQUEST_THRESHOLD`

`DIRECTUS_SLOW_REQUEST_THRESHOLD` defaults to `PT2S`. Requests slower than that produce a structured warning log with event `cms_directus_request_slow`.

Actuator now exposes:

- `/actuator/health`
- `/actuator/health/liveness`
- `/actuator/health/readiness`
- `/actuator/info`
- `/actuator/prometheus`

## Dashboards And Alerts

Committed monitoring assets:

- Grafana dashboard: `monitoring/grafana/directus-cms-dashboard.json`
- Prometheus alerts: `monitoring/prometheus/cms-alerts.yml`
- Prometheus scrape example: `monitoring/prometheus/eshop-api-scrape.example.yml`

Recommended dashboard focus:

- CMS facade 5xx rate
- CMS facade p95 latency
- Directus upstream error rate
- Directus upstream p95 latency
- CMS cache hit rate
- CMS request volume by endpoint

Committed alert thresholds:

- CMS facade 5xx error rate over 2% for 10 minutes
- CMS facade p95 latency over 1.5s for 10 minutes
- Directus upstream error rate over 5% for 10 minutes
- Directus upstream p95 latency over 1.2s for 10 minutes
- CMS cache hit rate below 50% for 15 minutes

Adjust those numbers after you observe real production traffic.

## Structured Logs

The backend now emits CMS logs with searchable key-value pairs in the console output.

Important event keys:

- `event=cms_directus_request_failed`
- `event=cms_directus_request_slow`
- `event=cms_directus_content_not_found`
- `event=cms_cache_invalidation`

Useful fields:

- `component=cms`
- `operation=<site_settings|navigation_groups|page|page_sections|files|...>`
- `access_mode=<published|preview|system>`
- `path=<directus-path>`
- `duration_ms=<value>`
- `status=<http-status>` for HTTP failures
- `error_type=<network|exception|http_500|...>`

Example search patterns:

```text
event=cms_directus_request_failed component=cms
event=cms_directus_request_slow operation=page
event=cms_cache_invalidation scope=all
```

## Log Search Scope

For complete CMS incident coverage, search these container logs together:

- `api`
  Backend Directus fetch failures, cache behavior, CMS latency
- `directus`
  Directus runtime errors and auth failures
- `keycloak`
  Editor SSO failures for the `directus` client

The backend repo only controls the `api` log format directly. Directus and Keycloak logs still need to be shipped by your container logging path.

Minimum operator commands on the VM:

```bash
docker compose --env-file .env -f docker-compose.prod.yml logs --tail 200 api
docker compose --env-file .env -f docker-compose.prod.yml logs --tail 200 directus
docker compose --env-file .env -f docker-compose.prod.yml logs --tail 200 keycloak
```

If you already use centralized logging outside this repo, ingest those same container streams there and search by the fields listed above.

## Editor Login Failures

Editor login failures do not originate in the backend CMS facade. They show up in:

- Directus logs for failed auth callback/external provider errors
- Keycloak logs for the `directus` client in the `cozyhome` realm

That is why the recommended searchable-log scope includes both `directus` and `keycloak`, not only `api`.
