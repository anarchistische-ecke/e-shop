#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"
COMPOSE_FILE="$ROOT_DIR/docker-compose.prod.yml"
WAIT_TIMEOUT_SECONDS=120
WAIT_INTERVAL_SECONDS=5
API_URL=""
DIRECTUS_URL=""
STOREFRONT_URL=""
CONTENT_URL=""
PUBLIC_API_URL=""
PUBLIC_DIRECTUS_URL=""
PUBLIC_STOREFRONT_URL=""
PUBLIC_CONTENT_URL=""
SKIP_PUBLIC=false
VERIFY_RUNTIME_STATE=false

# shellcheck source=scripts/lib/env-file.sh
source "$ROOT_DIR/scripts/lib/env-file.sh"
# shellcheck source=scripts/lib/runtime-release.sh
source "$ROOT_DIR/scripts/lib/runtime-release.sh"

usage() {
  cat <<'EOF'
Usage:
  ./scripts/check-stack-health.sh \
    [--env-file <path>] \
    [--compose-file <path>] \
    [--api-url <url>] \
    [--directus-url <url>] \
    [--storefront-url <url>] \
    [--content-url <url>] \
    [--public-api-url <url>] \
    [--public-directus-url <url>] \
    [--public-storefront-url <url>] \
    [--public-content-url <url>] \
    [--skip-public] \
    [--verify-runtime-state] \
    [--timeout-seconds <seconds>]
EOF
}

normalize_url() {
  printf '%s\n' "${1%/}"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file)
      ENV_FILE="$(resolve_env_file_path "$2")"
      shift 2
      ;;
    --compose-file)
      COMPOSE_FILE="$(resolve_env_file_path "$2")"
      shift 2
      ;;
    --api-url)
      API_URL="$2"
      shift 2
      ;;
    --directus-url)
      DIRECTUS_URL="$2"
      shift 2
      ;;
    --storefront-url)
      STOREFRONT_URL="$2"
      shift 2
      ;;
    --content-url)
      CONTENT_URL="$2"
      shift 2
      ;;
    --public-api-url)
      PUBLIC_API_URL="$2"
      shift 2
      ;;
    --public-directus-url)
      PUBLIC_DIRECTUS_URL="$2"
      shift 2
      ;;
    --public-storefront-url)
      PUBLIC_STOREFRONT_URL="$2"
      shift 2
      ;;
    --public-content-url)
      PUBLIC_CONTENT_URL="$2"
      shift 2
      ;;
    --skip-public)
      SKIP_PUBLIC=true
      shift
      ;;
    --verify-runtime-state)
      VERIFY_RUNTIME_STATE=true
      shift
      ;;
    --timeout-seconds)
      WAIT_TIMEOUT_SECONDS="$2"
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "Unsupported argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing env file: $ENV_FILE" >&2
  exit 1
fi

if [[ ! -f "$COMPOSE_FILE" ]]; then
  echo "Missing compose file: $COMPOSE_FILE" >&2
  exit 1
fi

load_env_file "$ENV_FILE"
runtime_set_defaults
runtime_prepare_dirs
runtime_load_state

check_url() {
  local label="$1"
  local url="$2"
  local attempts

  attempts=$(( WAIT_TIMEOUT_SECONDS / WAIT_INTERVAL_SECONDS ))
  if (( attempts < 1 )); then
    attempts=1
  fi

  for (( attempt=1; attempt<=attempts; attempt+=1 )); do
    if curl --silent --show-error --fail --max-time 5 "$url" >/dev/null; then
      echo "[ok] $label -> $url"
      return 0
    fi

    sleep "$WAIT_INTERVAL_SECONDS"
  done

  echo "[fail] $label -> $url" >&2
  return 1
}

verify_runtime_state() {
  local upstream_api_port upstream_directus_port upstream_storefront_port

  if [[ -z "${CURRENT_LIVE_API_PORT:-}" || -z "${CURRENT_LIVE_DIRECTUS_PORT:-}" || -z "${CURRENT_LIVE_STOREFRONT_PORT:-}" ]]; then
    echo "Runtime state is incomplete; cannot verify nginx upstreams." >&2
    return 1
  fi

  upstream_api_port="$(runtime_read_upstream_port "$DEPLOY_NGINX_API_UPSTREAM_INCLUDE" || true)"
  upstream_directus_port="$(runtime_read_upstream_port "$DEPLOY_NGINX_CMS_UPSTREAM_INCLUDE" || true)"
  upstream_storefront_port="$(runtime_read_upstream_port "$DEPLOY_NGINX_STOREFRONT_UPSTREAM_INCLUDE" || true)"

  if [[ "$upstream_api_port" != "$CURRENT_LIVE_API_PORT" ]]; then
    echo "API upstream drift detected: nginx points to ${upstream_api_port:-unset}, state expects ${CURRENT_LIVE_API_PORT}." >&2
    return 1
  fi

  if [[ "$upstream_directus_port" != "$CURRENT_LIVE_DIRECTUS_PORT" ]]; then
    echo "CMS upstream drift detected: nginx points to ${upstream_directus_port:-unset}, state expects ${CURRENT_LIVE_DIRECTUS_PORT}." >&2
    return 1
  fi

  if [[ "$upstream_storefront_port" != "$CURRENT_LIVE_STOREFRONT_PORT" ]]; then
    echo "Storefront upstream drift detected: nginx points to ${upstream_storefront_port:-unset}, state expects ${CURRENT_LIVE_STOREFRONT_PORT}." >&2
    return 1
  fi

  echo "[ok] Runtime state matches nginx upstream includes."
}

if [[ -n "${CURRENT_LIVE_API_PORT:-}" && -n "${CURRENT_LIVE_DIRECTUS_PORT:-}" && -n "${CURRENT_LIVE_STOREFRONT_PORT:-}" ]]; then
  API_URL="${API_URL:-$(runtime_internal_api_url "$CURRENT_LIVE_API_PORT")}"
  DIRECTUS_URL="${DIRECTUS_URL:-$(runtime_internal_directus_url "$CURRENT_LIVE_DIRECTUS_PORT")}"
  STOREFRONT_URL="${STOREFRONT_URL:-$(runtime_internal_storefront_url "$CURRENT_LIVE_STOREFRONT_PORT")}"
  CONTENT_URL="${CONTENT_URL:-$(runtime_internal_content_url "$CURRENT_LIVE_API_PORT")}"
else
  API_URL="${API_URL:-${API_HEALTHCHECK_URL:-http://127.0.0.1:8080/health/redis}}"
  DIRECTUS_URL="${DIRECTUS_URL:-${DIRECTUS_HEALTHCHECK_URL:-http://127.0.0.1:8055/server/health}}"
  if [[ -n "${STOREFRONT_HEALTHCHECK_URL:-}" ]]; then
    STOREFRONT_URL="${STOREFRONT_URL:-$STOREFRONT_HEALTHCHECK_URL}"
  elif [[ -n "${STOREFRONT_HOST_PORT:-}" || -n "${STOREFRONT_IMAGE_REPOSITORY:-}" ]]; then
    STOREFRONT_URL="${STOREFRONT_URL:-http://127.0.0.1:${STOREFRONT_HOST_PORT:-3000}/healthz}"
  fi
  CONTENT_URL="${CONTENT_URL:-${CONTENT_HEALTHCHECK_URL:-}}"
fi

PUBLIC_API_URL="${PUBLIC_API_URL:-${PUBLIC_API_HEALTHCHECK_URL:-}}"
PUBLIC_DIRECTUS_URL="${PUBLIC_DIRECTUS_URL:-$(runtime_public_directus_url)}"
PUBLIC_STOREFRONT_URL="${PUBLIC_STOREFRONT_URL:-$(runtime_public_storefront_url)}"
PUBLIC_CONTENT_URL="${PUBLIC_CONTENT_URL:-${PUBLIC_CONTENT_HEALTHCHECK_URL:-}}"

echo "Checking internal runtime health..."
check_url "Backend health" "$API_URL"
check_url "Directus health" "$DIRECTUS_URL"

if [[ -n "$STOREFRONT_URL" ]]; then
  check_url "Storefront health" "$STOREFRONT_URL"
else
  echo "[skip] Storefront health URL is not configured."
fi

if [[ -n "$CONTENT_URL" ]]; then
  check_url "CMS facade health" "$CONTENT_URL"
fi

if [[ "$VERIFY_RUNTIME_STATE" == "true" ]]; then
  verify_runtime_state
fi

if [[ "$SKIP_PUBLIC" != "true" ]]; then
  echo "Checking public edge health..."

  if [[ -n "$PUBLIC_API_URL" ]]; then
    check_url "Public backend health" "$PUBLIC_API_URL"
  else
    echo "[skip] Public backend health URL is not configured."
  fi

  if [[ -n "$PUBLIC_DIRECTUS_URL" ]]; then
    check_url "Public Directus health" "$PUBLIC_DIRECTUS_URL"
  else
    echo "[skip] Public Directus health URL is not configured."
  fi

  if [[ -n "$PUBLIC_STOREFRONT_URL" ]]; then
    check_url "Public storefront health" "$PUBLIC_STOREFRONT_URL"
  else
    echo "[skip] Public storefront health URL is not configured."
  fi

  if [[ -n "$PUBLIC_CONTENT_URL" ]]; then
    check_url "Public CMS facade health" "$PUBLIC_CONTENT_URL"
  else
    echo "[skip] Public CMS facade health URL is not configured."
  fi
fi

echo "Stack health checks passed."
