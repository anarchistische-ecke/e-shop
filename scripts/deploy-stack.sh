#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"
COMPOSE_FILE="$ROOT_DIR/docker-compose.prod.yml"
BACKUP_DIR="$ROOT_DIR/backups/directus"
SKIP_BACKUP=false

usage() {
  cat <<'EOF'
Usage:
  ./scripts/deploy-stack.sh [--env-file <path>] [--compose-file <path>] [--backup-dir <path>] [--skip-backup]
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
    --backup-dir)
      BACKUP_DIR="$(resolve_path "$2")"
      shift 2
      ;;
    --skip-backup)
      SKIP_BACKUP=true
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

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing env file: $ENV_FILE" >&2
  exit 1
fi

if [[ ! -f "$COMPOSE_FILE" ]]; then
  echo "Missing compose file: $COMPOSE_FILE" >&2
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

echo "Starting shared infrastructure services..."
compose up -d postgres redis

if [[ "$SKIP_BACKUP" != "true" ]]; then
  echo "Backing up Directus database if it already exists..."
  bash "$ROOT_DIR/scripts/directus-db-backup.sh" \
    --env-file "$ENV_FILE" \
    --compose-file "$COMPOSE_FILE" \
    --output-dir "$BACKUP_DIR"
fi

echo "Ensuring Directus database and role exist..."
bash "$ROOT_DIR/scripts/directus-db-init.sh" \
  --env-file "$ENV_FILE" \
  --compose-file "$COMPOSE_FILE"

echo "Pulling pinned Directus image..."
compose pull directus

echo "Deploying API and Directus containers..."
compose up -d --build api directus

echo "Applying committed Directus schema snapshot..."
if ! compose exec -T -e DIRECTUS_BASE_URL=http://127.0.0.1:8055 directus \
  node /opt/directus-deploy/scripts/directus-schema.js apply; then
  echo "Directus schema apply failed. If default Directus login is disabled, set DIRECTUS_SCHEMA_ADMIN_TOKEN in the deployment .env." >&2
  exit 1
fi

echo "Bootstrapping Directus roles, permissions, and operator workspace defaults..."
bash "$ROOT_DIR/scripts/directus-governance-bootstrap.sh" \
  --env-file "$ENV_FILE" \
  --compose-file "$COMPOSE_FILE" \
  --database-service postgres

echo "Bootstrapping automatic published_at handling..."
bash "$ROOT_DIR/scripts/directus-published-at-bootstrap.sh" \
  --env-file "$ENV_FILE" \
  --compose-file "$COMPOSE_FILE" \
  --database-service postgres

echo "Running stack health checks..."
bash "$ROOT_DIR/scripts/check-stack-health.sh" \
  --env-file "$ENV_FILE" \
  --compose-file "$COMPOSE_FILE"

echo "Deployment complete."
compose ps
