#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"
COMPOSE_FILE="$ROOT_DIR/docker-compose.prod.yml"
DATABASE_SERVICE="postgres"
PUBLISHED_AT_FUNCTION="directus_set_cms_published_at"

usage() {
  cat <<'EOF'
Usage:
  ./scripts/directus-published-at-bootstrap.sh [--env-file <path>] [--compose-file <path>] [--database-service <name>]
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
    --env-file)
      ENV_FILE="$(resolve_path "$2")"
      shift 2
      ;;
    --compose-file)
      COMPOSE_FILE="$(resolve_path "$2")"
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

set -a
. "$ENV_FILE"
set +a

DIRECTUS_CMS_CONTENT_COLLECTIONS="${DIRECTUS_CMS_CONTENT_COLLECTIONS:-}"
DIRECTUS_CMS_PUBLIC_COLLECTIONS="${DIRECTUS_CMS_PUBLIC_COLLECTIONS:-$DIRECTUS_CMS_CONTENT_COLLECTIONS}"
DIRECTUS_CMS_STATUS_FIELD="${DIRECTUS_CMS_STATUS_FIELD:-status}"
DIRECTUS_PUBLISHED_AT_FIELD="${DIRECTUS_PUBLISHED_AT_FIELD:-published_at}"
DIRECTUS_DB_DATABASE="${DIRECTUS_DB_DATABASE:-directus}"
DIRECTUS_DB_USER="${DIRECTUS_DB_USER:-directus}"
DIRECTUS_DB_PASSWORD="${DIRECTUS_DB_PASSWORD:-}"

if [[ -z "$DIRECTUS_CMS_PUBLIC_COLLECTIONS" ]]; then
  echo "DIRECTUS_CMS_PUBLIC_COLLECTIONS or DIRECTUS_CMS_CONTENT_COLLECTIONS must be set in $ENV_FILE." >&2
  exit 1
fi

if [[ "$DIRECTUS_CMS_STATUS_FIELD" != "status" ]]; then
  echo "Unsupported DIRECTUS_CMS_STATUS_FIELD=$DIRECTUS_CMS_STATUS_FIELD. This bootstrap currently requires status." >&2
  exit 1
fi

if [[ "$DIRECTUS_PUBLISHED_AT_FIELD" != "published_at" ]]; then
  echo "Unsupported DIRECTUS_PUBLISHED_AT_FIELD=$DIRECTUS_PUBLISHED_AT_FIELD. This bootstrap currently requires published_at." >&2
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

echo "Ensuring automatic published_at triggers on governed CMS collections..."

compose exec -T -e PGPASSWORD="$DIRECTUS_DB_PASSWORD" "$DATABASE_SERVICE" \
  psql -v ON_ERROR_STOP=1 -U "$DIRECTUS_DB_USER" -d "$DIRECTUS_DB_DATABASE" <<SQL
CREATE OR REPLACE FUNCTION public.${PUBLISHED_AT_FUNCTION}()
RETURNS trigger
LANGUAGE plpgsql
AS \$\$
BEGIN
  IF NEW.published_at IS DISTINCT FROM NULL AND NEW.status <> 'published' THEN
    NEW.published_at = NULL;
  ELSIF NEW.status = 'published' AND NEW.published_at IS NULL THEN
    IF TG_OP = 'UPDATE' AND OLD.status = 'published' AND OLD.published_at IS NOT NULL THEN
      NEW.published_at = OLD.published_at;
    ELSE
      NEW.published_at = CURRENT_TIMESTAMP;
    END IF;
  END IF;

  RETURN NEW;
END;
\$\$;
SQL

while IFS= read -r collection; do
  trigger_name="cms_set_published_at_${collection}"
  has_columns="$(
    compose exec -T -e PGPASSWORD="$DIRECTUS_DB_PASSWORD" "$DATABASE_SERVICE" \
      psql -At -v ON_ERROR_STOP=1 -v collection="$collection" -U "$DIRECTUS_DB_USER" -d "$DIRECTUS_DB_DATABASE" <<'SQL'
SELECT CASE
  WHEN EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = :'collection'
      AND column_name = 'status'
  ) AND EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = :'collection'
      AND column_name = 'published_at'
  )
  THEN 'yes'
  ELSE 'no'
END;
SQL
  )"

  if [[ "$has_columns" != "yes" ]]; then
    echo "Skipping public.$collection because status/published_at columns are missing."
    continue
  fi

  compose exec -T -e PGPASSWORD="$DIRECTUS_DB_PASSWORD" "$DATABASE_SERVICE" \
    psql -v ON_ERROR_STOP=1 -v collection="$collection" -v trigger_name="$trigger_name" \
      -U "$DIRECTUS_DB_USER" -d "$DIRECTUS_DB_DATABASE" <<SQL
DROP TRIGGER IF EXISTS :"trigger_name" ON :"collection";
CREATE TRIGGER :"trigger_name"
BEFORE INSERT OR UPDATE ON :"collection"
FOR EACH ROW
EXECUTE FUNCTION public.${PUBLISHED_AT_FUNCTION}();
SQL
  echo "Ensured automatic published_at trigger on public.$collection"
done < <(normalize_csv "$DIRECTUS_CMS_PUBLIC_COLLECTIONS")

echo "Automatic published_at bootstrap completed."
