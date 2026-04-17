#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"
COMPOSE_FILE="$ROOT_DIR/docker-compose.prod.yml"
OUTPUT_DIR="$ROOT_DIR/backups/directus"
RETENTION_DAYS="${DIRECTUS_BACKUP_RETENTION_DAYS:-14}"

usage() {
  cat <<'EOF'
Usage:
  ./scripts/directus-db-backup.sh [--env-file <path>] [--compose-file <path>] [--output-dir <path>] [--retention-days <days>]
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
    --output-dir)
      OUTPUT_DIR="$(resolve_path "$2")"
      shift 2
      ;;
    --retention-days)
      RETENTION_DAYS="$2"
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

compose up -d postgres >/dev/null

if ! compose exec -T \
  -e PGPASSWORD="$POSTGRES_PASSWORD" \
  postgres \
  psql \
    --username "$POSTGRES_USER" \
    --dbname postgres \
    --tuples-only \
    --no-align \
    --command "SELECT 1 FROM pg_database WHERE datname = '$DIRECTUS_DB_DATABASE'" | grep -q "^1$"; then
  echo "Skipping Directus DB backup because database '$DIRECTUS_DB_DATABASE' does not exist yet."
  exit 0
fi

mkdir -p "$OUTPUT_DIR"

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
backup_path="$OUTPUT_DIR/directus-${timestamp}.sql.gz"

compose exec -T \
  -e PGPASSWORD="$POSTGRES_PASSWORD" \
  postgres \
  pg_dump \
    --username "$POSTGRES_USER" \
    --dbname "$DIRECTUS_DB_DATABASE" \
    --clean \
    --if-exists \
    --no-owner \
    --no-privileges | gzip -c >"$backup_path"

find "$OUTPUT_DIR" -type f -name 'directus-*.sql.gz' -mtime +"$RETENTION_DAYS" -delete

echo "Directus database backup written to $backup_path"
