#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"
COMPOSE_FILE="$ROOT_DIR/docker-compose.prod.yml"
WAIT_TIMEOUT_SECONDS=120
WAIT_INTERVAL_SECONDS=5
API_URL=""
DIRECTUS_URL=""
CONTENT_URL=""

usage() {
  cat <<'EOF'
Usage:
  ./scripts/check-stack-health.sh [--env-file <path>] [--compose-file <path>] [--api-url <url>] [--directus-url <url>] [--content-url <url>] [--timeout-seconds <seconds>]
EOF
}

resolve_path() {
  case "$1" in
    /*) printf '%s\n' "$1" ;;
    *) printf '%s\n' "$PWD/$1" ;;
  esac
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file)
      ENV_FILE="$(resolve_path "$2")"
      shift 2
      ;;
    --compose-file)
      COMPOSE_FILE="$(resolve_path "$2")"
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
    --content-url)
      CONTENT_URL="$2"
      shift 2
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

set -a
source "$ENV_FILE"
set +a

compose() {
  if docker compose version >/dev/null 2>&1; then
    docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
  elif command -v docker-compose >/dev/null 2>&1; then
    docker-compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
  else
    echo "Docker Compose not found." >&2
    exit 1
  fi
}

normalize_url() {
  printf '%s\n' "${1%/}"
}

API_URL="${API_URL:-${API_HEALTHCHECK_URL:-http://127.0.0.1:8080/health/redis}}"
DIRECTUS_URL="${DIRECTUS_URL:-${DIRECTUS_HEALTHCHECK_URL:-}}"
CONTENT_URL="${CONTENT_URL:-${CONTENT_HEALTHCHECK_URL:-}}"

if [[ -z "$DIRECTUS_URL" ]]; then
  if [[ -n "${DIRECTUS_PUBLIC_URL:-}" ]]; then
    DIRECTUS_URL="$(normalize_url "$DIRECTUS_PUBLIC_URL")/server/health"
  else
    DIRECTUS_URL="http://127.0.0.1:8055/server/health"
  fi
fi

check_url() {
  local label="$1"
  local url="$2"
  local attempts=$(( WAIT_TIMEOUT_SECONDS / WAIT_INTERVAL_SECONDS ))

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

echo "Checking Docker Compose service status..."
compose ps

check_url "Backend health" "$API_URL"
check_url "Directus health" "$DIRECTUS_URL"

if [[ -n "$CONTENT_URL" ]]; then
  check_url "CMS facade health" "$CONTENT_URL"
fi

echo "Stack health checks passed."
