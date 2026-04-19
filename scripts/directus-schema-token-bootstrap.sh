#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"
COMPOSE_FILE="$ROOT_DIR/docker-compose.prod.yml"
DATABASE_SERVICE="postgres"

# shellcheck source=scripts/lib/env-file.sh
source "$ROOT_DIR/scripts/lib/env-file.sh"

usage() {
  cat <<'EOF'
Usage:
  ./scripts/directus-schema-token-bootstrap.sh [--env-file <path>] [--compose-file <path>] [--database-service <name>]
EOF
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
    --database-service)
      DATABASE_SERVICE="$2"
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

: "${POSTGRES_USER:?Set POSTGRES_USER in $ENV_FILE}"
: "${POSTGRES_PASSWORD:?Set POSTGRES_PASSWORD in $ENV_FILE}"
: "${DIRECTUS_DB_DATABASE:?Set DIRECTUS_DB_DATABASE in $ENV_FILE}"
: "${DIRECTUS_ADMIN_EMAIL:?Set DIRECTUS_ADMIN_EMAIL in $ENV_FILE}"

AUTH_DISABLE_DEFAULT="${DIRECTUS_AUTH_DISABLE_DEFAULT:-true}"
SCHEMA_ADMIN_TOKEN="${DIRECTUS_SCHEMA_ADMIN_TOKEN:-${DIRECTUS_ADMIN_TOKEN:-${ADMIN_TOKEN:-}}}"

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

query_directus_scalar() {
  local sql="$1"

  compose exec -T \
    -e PGPASSWORD="$POSTGRES_PASSWORD" \
    "$DATABASE_SERVICE" \
    psql \
      --username "$POSTGRES_USER" \
      --dbname "$DIRECTUS_DB_DATABASE" \
      --tuples-only \
      --no-align \
      -v ON_ERROR_STOP=1 \
      -v admin_email="$DIRECTUS_ADMIN_EMAIL" <<SQL | tr -d '[:space:]\r'
$sql
SQL
}

if [[ -z "$SCHEMA_ADMIN_TOKEN" ]]; then
  if [[ "$AUTH_DISABLE_DEFAULT" == "true" ]]; then
    echo "DIRECTUS_AUTH_DISABLE_DEFAULT=true but DIRECTUS_SCHEMA_ADMIN_TOKEN is not set. Schema automation cannot authenticate." >&2
    exit 1
  fi

  echo "Skipping Directus schema token bootstrap because no DIRECTUS_SCHEMA_ADMIN_TOKEN is configured."
  exit 0
fi

compose up -d "$DATABASE_SERVICE" >/dev/null

wait_for_admin_user() {
  local count

  for _ in $(seq 1 30); do
    count="$(query_directus_scalar "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'directus_users';")"

    if [[ "$count" == "1" ]]; then
      count="$(query_directus_scalar "SELECT COUNT(*) FROM directus_users WHERE email = :'admin_email';")"

      if [[ "$count" == "1" ]]; then
        return 0
      fi
    fi

    sleep 2
  done

  return 1
}

if ! wait_for_admin_user; then
  echo "Timed out waiting for Directus admin user '${DIRECTUS_ADMIN_EMAIL}' in database '${DIRECTUS_DB_DATABASE}'." >&2
  exit 1
fi

compose exec -T \
  -e PGPASSWORD="$POSTGRES_PASSWORD" \
  "$DATABASE_SERVICE" \
  psql \
    --username "$POSTGRES_USER" \
    --dbname "$DIRECTUS_DB_DATABASE" \
    -v ON_ERROR_STOP=1 \
    -v admin_email="$DIRECTUS_ADMIN_EMAIL" \
    -v static_token="$SCHEMA_ADMIN_TOKEN" <<'SQL'
SELECT format(
  'UPDATE directus_users SET token = %L WHERE email = %L',
  :'static_token',
  :'admin_email'
) \gexec
SQL

echo "Seeded Directus schema automation token for ${DIRECTUS_ADMIN_EMAIL}."
