#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"
COMPOSE_FILE="$ROOT_DIR/docker-compose.prod.yml"
BACKUP_FILE=""
START_DIRECTUS_AFTER_RESTORE=true

# shellcheck source=scripts/lib/env-file.sh
source "$ROOT_DIR/scripts/lib/env-file.sh"

usage() {
  cat <<'EOF'
Usage:
  ./scripts/directus-db-restore.sh --backup-file <path> [--env-file <path>] [--compose-file <path>] [--no-start-directus]
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --backup-file)
      BACKUP_FILE="$(resolve_env_file_path "$2")"
      shift 2
      ;;
    --env-file)
      ENV_FILE="$(resolve_env_file_path "$2")"
      shift 2
      ;;
    --compose-file)
      COMPOSE_FILE="$(resolve_env_file_path "$2")"
      shift 2
      ;;
    --no-start-directus)
      START_DIRECTUS_AFTER_RESTORE=false
      shift
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

if [[ -z "$BACKUP_FILE" ]]; then
  echo "Missing required --backup-file argument." >&2
  usage >&2
  exit 1
fi

if [[ ! -f "$BACKUP_FILE" ]]; then
  echo "Backup file does not exist: $BACKUP_FILE" >&2
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

load_env_file "$ENV_FILE"

: "${POSTGRES_USER:?Set POSTGRES_USER in $ENV_FILE}"
: "${POSTGRES_PASSWORD:?Set POSTGRES_PASSWORD in $ENV_FILE}"
: "${DIRECTUS_DB_DATABASE:?Set DIRECTUS_DB_DATABASE in $ENV_FILE}"

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

decompress_backup() {
  case "$BACKUP_FILE" in
    *.gz) gzip --decompress --stdout "$BACKUP_FILE" ;;
    *) cat "$BACKUP_FILE" ;;
  esac
}

echo "Starting PostgreSQL..."
compose up -d postgres >/dev/null

echo "Stopping Directus before restore..."
compose stop directus >/dev/null 2>&1 || true

echo "Ensuring Directus database exists..."
bash "$ROOT_DIR/scripts/directus-db-init.sh" \
  --env-file "$ENV_FILE" \
  --compose-file "$COMPOSE_FILE"

echo "Restoring Directus database from $BACKUP_FILE ..."
decompress_backup | compose exec -T postgres sh -lc '
  export PGPASSWORD="$POSTGRES_PASSWORD"
  exec psql \
    --username "$POSTGRES_USER" \
    --dbname "$DIRECTUS_DB_DATABASE" \
    -v ON_ERROR_STOP=1
'

if [[ "$START_DIRECTUS_AFTER_RESTORE" == "true" ]]; then
  echo "Starting Directus after restore..."
  compose up -d directus >/dev/null
fi

echo "Directus database restore completed."
