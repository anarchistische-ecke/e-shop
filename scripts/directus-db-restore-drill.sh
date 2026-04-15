#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BACKUP_FILE=""
ENV_FILE="$ROOT_DIR/.env"
POSTGRES_IMAGE="${DIRECTUS_RESTORE_DRILL_POSTGRES_IMAGE:-postgres:16-alpine}"
POSTGRES_DB="${DIRECTUS_RESTORE_DRILL_DB:-directus}"
POSTGRES_USER="${DIRECTUS_RESTORE_DRILL_USER:-directus}"
POSTGRES_PASSWORD="${DIRECTUS_RESTORE_DRILL_PASSWORD:-directus}"
TIMEOUT_SECONDS="${DIRECTUS_RESTORE_DRILL_TIMEOUT_SECONDS:-120}"
CONTAINER_NAME="directus-restore-drill-$RANDOM-$RANDOM"

usage() {
  cat <<'EOF'
Usage:
  ./scripts/directus-db-restore-drill.sh --backup-file <path> [--env-file <path>] [--postgres-image <image>] [--timeout-seconds <seconds>]
EOF
}

resolve_path() {
  case "$1" in
    /*) printf '%s\n' "$1" ;;
    *) printf '%s\n' "$PWD/$1" ;;
  esac
}

normalize_csv() {
  printf '%s' "$1" \
    | tr ',' '\n' \
    | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' \
    | awk 'NF' \
    | sort -u
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --backup-file)
      BACKUP_FILE="$(resolve_path "$2")"
      shift 2
      ;;
    --env-file)
      ENV_FILE="$(resolve_path "$2")"
      shift 2
      ;;
    --postgres-image)
      POSTGRES_IMAGE="$2"
      shift 2
      ;;
    --timeout-seconds)
      TIMEOUT_SECONDS="$2"
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

if [[ -z "$BACKUP_FILE" ]]; then
  echo "Missing required --backup-file argument." >&2
  usage >&2
  exit 1
fi

if [[ ! -f "$BACKUP_FILE" ]]; then
  echo "Backup file does not exist: $BACKUP_FILE" >&2
  exit 1
fi

if [[ -f "$ENV_FILE" ]]; then
  set -a
  source "$ENV_FILE"
  set +a
fi

DIRECTUS_CMS_PUBLIC_COLLECTIONS="${DIRECTUS_CMS_PUBLIC_COLLECTIONS:-${DIRECTUS_CMS_CONTENT_COLLECTIONS:-}}"

cleanup() {
  docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
}

decompress_backup() {
  case "$BACKUP_FILE" in
    *.gz) gzip --decompress --stdout "$BACKUP_FILE" ;;
    *) cat "$BACKUP_FILE" ;;
  esac
}

wait_for_postgres() {
  local attempts=$(( TIMEOUT_SECONDS / 2 ))

  if (( attempts < 1 )); then
    attempts=1
  fi

  for (( attempt=1; attempt<=attempts; attempt+=1 )); do
    if docker exec "$CONTAINER_NAME" pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done

  echo "Timed out waiting for disposable Postgres restore container." >&2
  exit 1
}

trap cleanup EXIT

echo "Starting disposable Postgres restore container $CONTAINER_NAME ..."
docker run -d --rm \
  --name "$CONTAINER_NAME" \
  -e "POSTGRES_DB=$POSTGRES_DB" \
  -e "POSTGRES_USER=$POSTGRES_USER" \
  -e "POSTGRES_PASSWORD=$POSTGRES_PASSWORD" \
  "$POSTGRES_IMAGE" >/dev/null

wait_for_postgres

echo "Restoring $BACKUP_FILE into disposable Postgres ..."
decompress_backup | docker exec -i "$CONTAINER_NAME" sh -lc '
  export PGPASSWORD="$POSTGRES_PASSWORD"
  exec psql \
    --username "$POSTGRES_USER" \
    --dbname "$POSTGRES_DB" \
    -v ON_ERROR_STOP=1
'

echo "Validating restored Directus system tables ..."
system_table_check="$(
  docker exec "$CONTAINER_NAME" sh -lc '
    export PGPASSWORD="$POSTGRES_PASSWORD"
    psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --tuples-only --no-align -v ON_ERROR_STOP=1 \
      --command "
        SELECT CASE
          WHEN to_regclass('\''public.directus_users'\'') IS NOT NULL
           AND to_regclass('\''public.directus_collections'\'') IS NOT NULL
           AND to_regclass('\''public.directus_fields'\'') IS NOT NULL
          THEN '\''ok'\''
          ELSE '\''missing'\''
        END;
      "
  ' | tr -d '[:space:]'
)"

if [[ "$system_table_check" != "ok" ]]; then
  echo "Restore drill failed: missing required Directus system tables after restore." >&2
  exit 1
fi

user_count="$(
  docker exec "$CONTAINER_NAME" sh -lc '
    export PGPASSWORD="$POSTGRES_PASSWORD"
    psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --tuples-only --no-align -v ON_ERROR_STOP=1 \
      --command "SELECT COUNT(*) FROM public.directus_users;"
  ' | tr -d '[:space:]'
)"

collection_count="$(
  docker exec "$CONTAINER_NAME" sh -lc '
    export PGPASSWORD="$POSTGRES_PASSWORD"
    psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --tuples-only --no-align -v ON_ERROR_STOP=1 \
      --command "SELECT COUNT(*) FROM public.directus_collections;"
  ' | tr -d '[:space:]'
)"

if [[ -n "$DIRECTUS_CMS_PUBLIC_COLLECTIONS" ]]; then
  echo "Validating governed CMS tables from DIRECTUS_CMS_PUBLIC_COLLECTIONS ..."
  while IFS= read -r collection; do
    [[ -n "$collection" ]] || continue

    if [[ ! "$collection" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
      echo "Unsupported collection name in DIRECTUS_CMS_PUBLIC_COLLECTIONS: $collection" >&2
      exit 1
    fi

    exists="$(
      docker exec "$CONTAINER_NAME" sh -lc "
        export PGPASSWORD="$POSTGRES_PASSWORD"
        psql --username \"$POSTGRES_USER\" --dbname \"$POSTGRES_DB\" --tuples-only --no-align -v ON_ERROR_STOP=1 \
          --command \"SELECT CASE WHEN to_regclass('public.$collection') IS NOT NULL THEN 'ok' ELSE 'missing' END;\"
      " | tr -d '[:space:]'
    )"

    if [[ "$exists" != "ok" ]]; then
      echo "Restore drill failed: missing governed CMS table public.$collection after restore." >&2
      exit 1
    fi
  done < <(normalize_csv "$DIRECTUS_CMS_PUBLIC_COLLECTIONS")
fi

echo "Restore drill succeeded."
echo "Validated backup: $BACKUP_FILE"
echo "Restored users: ${user_count:-0}"
echo "Restored Directus collections: ${collection_count:-0}"
