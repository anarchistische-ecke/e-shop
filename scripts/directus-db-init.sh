#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"
COMPOSE_FILE="$ROOT_DIR/docker-compose.prod.yml"

# shellcheck source=scripts/lib/env-file.sh
source "$ROOT_DIR/scripts/lib/env-file.sh"

usage() {
  cat <<'EOF'
Usage:
  ./scripts/directus-db-init.sh [--env-file <path>] [--compose-file <path>]
EOF
}

extract_database_name_from_jdbc_url() {
  local jdbc_url="$1"

  if [[ "$jdbc_url" =~ ^jdbc:postgresql://[^/]+/([^?]+) ]]; then
    printf '%s\n' "${BASH_REMATCH[1]}"
    return 0
  fi

  return 1
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
: "${DIRECTUS_DB_USER:?Set DIRECTUS_DB_USER in $ENV_FILE}"
: "${DIRECTUS_DB_PASSWORD:?Set DIRECTUS_DB_PASSWORD in $ENV_FILE}"

COMMERCE_DB_DATABASE="${ESHOP_DB_DATABASE:-}"
if [[ -z "$COMMERCE_DB_DATABASE" && -n "${SPRING_DATASOURCE_URL:-}" ]]; then
  COMMERCE_DB_DATABASE="$(extract_database_name_from_jdbc_url "$SPRING_DATASOURCE_URL" || true)"
fi
COMMERCE_DB_DATABASE="${COMMERCE_DB_DATABASE:-${POSTGRES_DB:-}}"
COMMERCE_DB_USER="${ESHOP_DB_USER:-${SPRING_DATASOURCE_USERNAME:-${POSTGRES_USER}}}"
COMMERCE_DB_PASSWORD="${ESHOP_DB_PASSWORD:-${SPRING_DATASOURCE_PASSWORD:-}}"

: "${COMMERCE_DB_DATABASE:?Set ESHOP_DB_DATABASE or SPRING_DATASOURCE_URL/POSTGRES_DB in $ENV_FILE}"
: "${COMMERCE_DB_USER:?Set ESHOP_DB_USER or SPRING_DATASOURCE_USERNAME in $ENV_FILE}"

if [[ "$DIRECTUS_DB_DATABASE" == "$COMMERCE_DB_DATABASE" ]]; then
  echo "DIRECTUS_DB_DATABASE and the commerce database must be different." >&2
  exit 1
fi

if [[ "$DIRECTUS_DB_USER" == "$COMMERCE_DB_USER" ]]; then
  echo "DIRECTUS_DB_USER and the commerce runtime user must be different." >&2
  exit 1
fi

if [[ "$DIRECTUS_DB_USER" == "$POSTGRES_USER" ]]; then
  echo "DIRECTUS_DB_USER must not reuse the PostgreSQL bootstrap/admin role." >&2
  exit 1
fi

if [[ "$COMMERCE_DB_USER" == "$POSTGRES_USER" ]]; then
  echo "Warning: commerce runtime user matches the PostgreSQL bootstrap/admin role." >&2
  echo "Least-privilege for the backend is not fully enforced until SPRING_DATASOURCE_USERNAME or ESHOP_DB_USER uses a separate role." >&2
fi

if [[ "$COMMERCE_DB_USER" != "$POSTGRES_USER" && -z "$COMMERCE_DB_PASSWORD" ]]; then
  echo "Set ESHOP_DB_PASSWORD or SPRING_DATASOURCE_PASSWORD so the commerce runtime role can be provisioned." >&2
  exit 1
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

compose up -d postgres >/dev/null

compose exec -T \
  -e PGPASSWORD="$POSTGRES_PASSWORD" \
  postgres \
  psql \
    --username "$POSTGRES_USER" \
    --dbname postgres \
    -v ON_ERROR_STOP=1 \
    -v admin_user="$POSTGRES_USER" \
    -v commerce_db="$COMMERCE_DB_DATABASE" \
    -v commerce_user="$COMMERCE_DB_USER" \
    -v commerce_password="$COMMERCE_DB_PASSWORD" \
    -v directus_db="$DIRECTUS_DB_DATABASE" \
    -v directus_user="$DIRECTUS_DB_USER" \
    -v directus_password="$DIRECTUS_DB_PASSWORD" <<'SQL'
SELECT
  format(
    'CREATE ROLE %I LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION',
    :'commerce_user',
    :'commerce_password'
  )
WHERE :'commerce_user' <> :'admin_user'
  AND NOT EXISTS (
    SELECT 1
    FROM pg_roles
    WHERE rolname = :'commerce_user'
) \gexec

SELECT
  format(
    'ALTER ROLE %I WITH LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION',
    :'commerce_user',
    :'commerce_password'
  )
WHERE :'commerce_user' <> :'admin_user' \gexec

SELECT
  format(
    'CREATE ROLE %I LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION',
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
    'ALTER ROLE %I WITH LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION',
    :'directus_user',
    :'directus_password'
  ) \gexec

SELECT
  format(
    'CREATE DATABASE %I OWNER %I',
    :'directus_db',
    :'directus_user'
  )
WHERE NOT EXISTS (
  SELECT 1
  FROM pg_database
  WHERE datname = :'directus_db'
) \gexec

SELECT format('ALTER DATABASE %I OWNER TO %I', :'directus_db', :'directus_user') \gexec
SELECT format('REVOKE ALL ON DATABASE %I FROM PUBLIC', :'directus_db') \gexec
SELECT format('GRANT CONNECT, TEMP ON DATABASE %I TO %I', :'directus_db', :'directus_user') \gexec
SELECT format('REVOKE ALL ON DATABASE %I FROM %I', :'directus_db', :'commerce_user')
WHERE :'commerce_user' <> :'admin_user' \gexec
SELECT format('REVOKE ALL ON DATABASE %I FROM PUBLIC', :'commerce_db') \gexec
SELECT format('GRANT CONNECT, TEMP ON DATABASE %I TO %I', :'commerce_db', :'commerce_user') \gexec
SELECT format('REVOKE ALL ON DATABASE %I FROM %I', :'commerce_db', :'directus_user') \gexec
SQL

compose exec -T \
  -e PGPASSWORD="$POSTGRES_PASSWORD" \
  postgres \
  psql \
    --username "$POSTGRES_USER" \
    --dbname "$DIRECTUS_DB_DATABASE" \
    -v ON_ERROR_STOP=1 \
    -v admin_user="$POSTGRES_USER" \
    -v commerce_user="$COMMERCE_DB_USER" \
    -v directus_user="$DIRECTUS_DB_USER" <<'SQL'
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM PUBLIC;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM PUBLIC;
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA public FROM PUBLIC;
SELECT format('REVOKE ALL ON SCHEMA public FROM %I', :'commerce_user')
WHERE :'commerce_user' <> :'admin_user' \gexec
SELECT format('REVOKE ALL ON ALL TABLES IN SCHEMA public FROM %I', :'commerce_user')
WHERE :'commerce_user' <> :'admin_user' \gexec
SELECT format('REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM %I', :'commerce_user')
WHERE :'commerce_user' <> :'admin_user' \gexec
SELECT format('REVOKE ALL ON ALL FUNCTIONS IN SCHEMA public FROM %I', :'commerce_user')
WHERE :'commerce_user' <> :'admin_user' \gexec
SELECT format('ALTER SCHEMA public OWNER TO %I', :'directus_user') \gexec
SELECT format('GRANT ALL ON SCHEMA public TO %I', :'directus_user') \gexec
SQL

compose exec -T \
  -e PGPASSWORD="$POSTGRES_PASSWORD" \
  postgres \
  psql \
    --username "$POSTGRES_USER" \
    --dbname "$COMMERCE_DB_DATABASE" \
    -v ON_ERROR_STOP=1 \
    -v admin_user="$POSTGRES_USER" \
    -v commerce_user="$COMMERCE_DB_USER" \
    -v directus_user="$DIRECTUS_DB_USER" <<'SQL'
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM PUBLIC;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM PUBLIC;
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA public FROM PUBLIC;
SELECT format('REVOKE ALL ON SCHEMA public FROM %I', :'directus_user') \gexec
SELECT format('REVOKE ALL ON ALL TABLES IN SCHEMA public FROM %I', :'directus_user') \gexec
SELECT format('REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM %I', :'directus_user') \gexec
SELECT format('REVOKE ALL ON ALL FUNCTIONS IN SCHEMA public FROM %I', :'directus_user') \gexec
SELECT format('GRANT USAGE ON SCHEMA public TO %I', :'commerce_user') \gexec
SELECT format(
  'GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES, TRIGGER ON ALL TABLES IN SCHEMA public TO %I',
  :'commerce_user'
) \gexec
SELECT format(
  'GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO %I',
  :'commerce_user'
) \gexec
SELECT format('GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO %I', :'commerce_user') \gexec
SELECT format(
  'ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public REVOKE ALL ON TABLES FROM PUBLIC',
  :'admin_user'
) \gexec
SELECT format(
  'ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public REVOKE ALL ON SEQUENCES FROM PUBLIC',
  :'admin_user'
) \gexec
SELECT format(
  'ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public REVOKE ALL ON FUNCTIONS FROM PUBLIC',
  :'admin_user'
) \gexec
SELECT format(
  'ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES, TRIGGER ON TABLES TO %I',
  :'admin_user',
  :'commerce_user'
) \gexec
SELECT format(
  'ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO %I',
  :'admin_user',
  :'commerce_user'
) \gexec
SELECT format(
  'ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public GRANT EXECUTE ON FUNCTIONS TO %I',
  :'admin_user',
  :'commerce_user'
) \gexec
SQL

echo "Directus database '$DIRECTUS_DB_DATABASE' and commerce database '$COMMERCE_DB_DATABASE' are isolated and ready."
