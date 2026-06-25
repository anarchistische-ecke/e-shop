#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"
API_URL=""

# shellcheck source=scripts/lib/env-file.sh
source "$ROOT_DIR/scripts/lib/env-file.sh"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file) ENV_FILE="$(resolve_env_file_path "$2")"; shift 2 ;;
    --api-url) API_URL="$2"; shift 2 ;;
    --help|-h) echo "Usage: $0 [--env-file <path>] [--api-url <url>]"; exit 0 ;;
    *) echo "Unsupported argument: $1" >&2; exit 1 ;;
  esac
done

load_env_file "$ENV_FILE"
require_env YANDEX_STORAGE_BUCKET "$ENV_FILE"
require_env MEDIA_DERIVATIVES_BUCKET "$ENV_FILE"
require_env YANDEX_STORAGE_KEY "$ENV_FILE"
require_env YANDEX_STORAGE_SECRET "$ENV_FILE"

yc_bin="${YC_BIN:-$HOME/yandex-cloud/bin/yc}"
"$yc_bin" storage bucket get "$YANDEX_STORAGE_BUCKET" --full >/dev/null
"$yc_bin" storage bucket get "$MEDIA_DERIVATIVES_BUCKET" --full >/dev/null
"$ROOT_DIR/scripts/configure-media-upload-production.sh" --env-file "$ENV_FILE" --check

origin="${DIRECTUS_PUBLIC_URL:-https://cms.yug-postel.ru}"
options_response="$(mktemp)"
trap 'rm -f "$options_response"' EXIT
curl --silent --show-error --fail \
  -X OPTIONS \
  -H "Origin: $origin" \
  -H "Access-Control-Request-Method: PUT" \
  -H "Access-Control-Request-Headers: content-type" \
  -D "$options_response" \
  -o /dev/null \
  "https://storage.yandexcloud.net/${YANDEX_STORAGE_BUCKET}/${CATALOGUE_MEDIA_PENDING_PREFIX:-media-upload-pending}/preflight"
grep -Eiq '^access-control-allow-origin: .*' "$options_response"
grep -Eiq '^access-control-expose-headers: .*etag' "$options_response"

redis_container="$(docker ps --filter 'label=com.docker.compose.service=redis' --format '{{.Names}}' | head -n 1)"
if [[ -z "$redis_container" ]]; then
  echo "Redis container was not found." >&2
  exit 1
fi
docker exec "$redis_container" redis-cli ping | grep -qx PONG

"$yc_bin" cdn resource list --format json | grep -q '"cname": "img.yug-postel.ru"'

if [[ -n "$API_URL" ]]; then
  curl --silent --show-error --fail "${API_URL%/}/health/media" >/dev/null
fi

echo "Media upload preflight passed."
