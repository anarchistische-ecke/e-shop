#!/usr/bin/env bash
set -euo pipefail

RESOURCE_ID="${STOREFRONT_CDN_RESOURCE_ID:-}"
YC_BIN="${YC_BIN:-yc}"

if [[ -z "$RESOURCE_ID" ]]; then
  echo "STOREFRONT_CDN_RESOURCE_ID is required." >&2
  exit 1
fi

"$YC_BIN" cdn resource update "$RESOURCE_ID" \
  --cache-expiration-time-default 0 \
  --clear-browser-cache-expiration-time \
  --clear-query-params-options

"$YC_BIN" cdn cache purge \
  --resource-id "$RESOURCE_ID" \
  --path "/,/catalog,/catalog*,/category/*,/product/*,/content/*,/sitemap.xml"

echo "CDN origin-header caching enabled and dynamic paths purged for $RESOURCE_ID."
