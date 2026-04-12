#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"
COMPOSE_FILE="$ROOT_DIR/docker-compose.prod.yml"

usage() {
  cat <<'EOF'
Usage:
  ./scripts/directus-db-init.sh [--env-file <path>] [--compose-file <path>]
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
: "${DIRECTUS_DB_USER:?Set DIRECTUS_DB_USER in $ENV_FILE}"
: "${DIRECTUS_DB_PASSWORD:?Set DIRECTUS_DB_PASSWORD in $ENV_FILE}"

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

compose exec -T postgres sh -lc '
  export PGPASSWORD="$POSTGRES_PASSWORD"
  psql \
    --username "$POSTGRES_USER" \
    --dbname postgres \
    -v ON_ERROR_STOP=1 \
    -v directus_db="$DIRECTUS_DB_DATABASE" \
    -v directus_user="$DIRECTUS_DB_USER" \
    -v directus_password="$DIRECTUS_DB_PASSWORD" <<'"'"'SQL'"'"'
SELECT
  format(
    '"'"'CREATE ROLE %I LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION'"'"',
    :'directus_user',
    :'directus_password'
  )
WHERE NOT EXISTS (
  SELECT 1
  FROM pg_roles
  WHERE rolname = :'directus_user'
) \gexec

SELECT
  format(
    '"'"'ALTER ROLE %I WITH LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION'"'"',
    :'directus_user',
    :'directus_password'
  ) \gexec

SELECT
  format(
    '"'"'CREATE DATABASE %I OWNER %I'"'"',
    :'directus_db',
    :'directus_user'
  )
WHERE NOT EXISTS (
  SELECT 1
  FROM pg_database
  WHERE datname = :'directus_db'
) \gexec

SELECT format('"'"'ALTER DATABASE %I OWNER TO %I'"'"', :'directus_db', :'directus_user') \gexec
SELECT format('"'"'REVOKE ALL ON DATABASE %I FROM PUBLIC'"'"', :'directus_db') \gexec
SELECT format('"'"'GRANT CONNECT, TEMP ON DATABASE %I TO %I'"'"', :'directus_db', :'directus_user') \gexec
SQL
'

compose exec -T postgres sh -lc '
  export PGPASSWORD="$POSTGRES_PASSWORD"
  psql \
    --username "$POSTGRES_USER" \
    --dbname "$DIRECTUS_DB_DATABASE" \
    -v ON_ERROR_STOP=1 \
    -v directus_user="$DIRECTUS_DB_USER" <<'"'"'SQL'"'"'
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
SELECT format('"'"'ALTER SCHEMA public OWNER TO %I'"'"', :'directus_user') \gexec
SELECT format('"'"'GRANT ALL ON SCHEMA public TO %I'"'"', :'directus_user') \gexec
SQL
'

echo "Directus database '$DIRECTUS_DB_DATABASE' and role '$DIRECTUS_DB_USER' are ready."
