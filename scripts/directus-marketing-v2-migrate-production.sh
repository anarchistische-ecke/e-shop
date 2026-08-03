#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"
COMPOSE_FILE="$ROOT_DIR/docker-compose.prod.yml"
MODE="dry-run"
ASSERT_IDEMPOTENT="false"
REPORT_FILE=""
BACKUP_DIR="$ROOT_DIR/backups/directus"
CONTAINER_STOREFRONT_ROOT="/tmp/directus-marketing-v2-storefront"

# shellcheck source=scripts/lib/env-file.sh
source "$ROOT_DIR/scripts/lib/env-file.sh"

usage() {
  cat <<'EOF'
Usage:
  ./scripts/directus-marketing-v2-migrate-production.sh \
    [--mode dry-run|apply] \
    [--assert-idempotent] \
    [--env-file <path>] \
    [--compose-file <path>] \
    [--backup-dir <path>] \
    [--report-file <path>]

The apply mode always takes a Directus database backup first. Static legal
sources are copied from the deployed storefront container into a temporary
directory inside the Directus container. No Directus credential reaches the
host Node runtime or browser.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --mode)
      MODE="$2"
      shift 2
      ;;
    --assert-idempotent)
      ASSERT_IDEMPOTENT="true"
      shift
      ;;
    --env-file)
      ENV_FILE="$(resolve_env_file_path "$2")"
      shift 2
      ;;
    --compose-file)
      COMPOSE_FILE="$(resolve_env_file_path "$2")"
      shift 2
      ;;
    --backup-dir)
      BACKUP_DIR="$(resolve_env_file_path "$2")"
      shift 2
      ;;
    --report-file)
      REPORT_FILE="$(resolve_env_file_path "$2")"
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

if [[ "$MODE" != "dry-run" && "$MODE" != "apply" ]]; then
  echo "Mode must be dry-run or apply." >&2
  exit 1
fi

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing env file: $ENV_FILE" >&2
  exit 1
fi

if [[ ! -f "$COMPOSE_FILE" ]]; then
  echo "Missing compose file: $COMPOSE_FILE" >&2
  exit 1
fi

if [[ -z "$REPORT_FILE" ]]; then
  REPORT_FILE="$ROOT_DIR/.deploy-state/marketing-v2-migration-${MODE}-$(date -u +%Y%m%dT%H%M%SZ).json"
fi

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

mkdir -p "$(dirname "$REPORT_FILE")" "$ROOT_DIR/.deploy-state"
WORK_DIR="$(mktemp -d "$ROOT_DIR/.deploy-state/marketing-v2-migration.XXXXXX")"
DIRECTUS_CONTAINER="$(compose ps -q directus)"
STOREFRONT_CONTAINER="$(compose ps -q storefront)"

cleanup() {
  if [[ -n "${DIRECTUS_CONTAINER:-}" ]]; then
    docker exec "$DIRECTUS_CONTAINER" \
      rm -rf "$CONTAINER_STOREFRONT_ROOT" >/dev/null 2>&1 || true
  fi
  if [[ -n "${WORK_DIR:-}" && "$WORK_DIR" == "$ROOT_DIR"/.deploy-state/marketing-v2-migration.* ]]; then
    rm -rf "$WORK_DIR"
  fi
}
trap cleanup EXIT

if [[ -z "$DIRECTUS_CONTAINER" || -z "$STOREFRONT_CONTAINER" ]]; then
  echo "Directus and storefront containers must be running." >&2
  exit 1
fi

mkdir -p "$WORK_DIR/storefront/public"
docker cp \
  "$STOREFRONT_CONTAINER:/app/public/legal" \
  "$WORK_DIR/storefront/public/legal"
docker exec "$DIRECTUS_CONTAINER" \
  rm -rf "$CONTAINER_STOREFRONT_ROOT"
docker cp \
  "$WORK_DIR/storefront" \
  "$DIRECTUS_CONTAINER:$CONTAINER_STOREFRONT_ROOT"

if [[ "$MODE" == "apply" ]]; then
  bash "$ROOT_DIR/scripts/directus-db-backup.sh" \
    --env-file "$ENV_FILE" \
    --compose-file "$COMPOSE_FILE" \
    --output-dir "$BACKUP_DIR"
fi

migration_args=()
if [[ "$MODE" == "dry-run" ]]; then
  migration_args+=(--dry-run)
fi
if [[ "$ASSERT_IDEMPOTENT" == "true" ]]; then
  migration_args+=(--assert-idempotent)
fi

compose exec -T \
  -e DIRECTUS_BASE_URL=http://127.0.0.1:8055 \
  directus \
  node /opt/directus-deploy/scripts/directus-marketing-v2-migrate.js \
    "${migration_args[@]}" \
    --storefront-root "$CONTAINER_STOREFRONT_ROOT" \
  | tee "$REPORT_FILE"

echo "Marketing V2 ${MODE} report written to $REPORT_FILE"
