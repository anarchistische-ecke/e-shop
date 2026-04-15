#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
KEYCLOAK_ENV_FILE="$ROOT_DIR/keycloak/.env"
DIRECTUS_ENV_FILE="$ROOT_DIR/directus/.env"

ensure_env_file() {
  local env_file="$1"
  local example_file="$2"

  if [[ -f "$env_file" ]]; then
    return
  fi

  if [[ ! -f "$example_file" ]]; then
    echo "Missing example env file: $example_file" >&2
    exit 1
  fi

  cp "$example_file" "$env_file"
  echo "Created $env_file from $example_file"
}

ensure_env_file "$KEYCLOAK_ENV_FILE" "$ROOT_DIR/keycloak/.env.example"
ensure_env_file "$DIRECTUS_ENV_FILE" "$ROOT_DIR/directus/.env.example"

echo "Starting postgres + redis..."
docker compose -f "$ROOT_DIR/docker-compose.yml" up -d postgres redis

echo "Starting Keycloak..."
docker compose --env-file "$KEYCLOAK_ENV_FILE" -f "$ROOT_DIR/keycloak/docker-compose.yml" up -d

echo "Starting Directus..."
docker compose --env-file "$DIRECTUS_ENV_FILE" -f "$ROOT_DIR/directus/docker-compose.yml" up -d

echo "Applying version-controlled Directus schema..."
"$ROOT_DIR/scripts/directus-content-model-bootstrap.sh"

echo "Bootstrapping automatic published_at handling..."
"$ROOT_DIR/scripts/directus-published-at-bootstrap.sh" \
  --env-file "$DIRECTUS_ENV_FILE" \
  --compose-file "$ROOT_DIR/directus/docker-compose.yml" \
  --database-service database

echo "Bootstrapping Directus SSO and governance..."
"$ROOT_DIR/scripts/directus-sso-bootstrap.sh"

echo
echo "Infrastructure started."
echo "Backend infra:   docker compose -f $ROOT_DIR/docker-compose.yml ps"
echo "Keycloak:        docker compose --env-file $KEYCLOAK_ENV_FILE -f $ROOT_DIR/keycloak/docker-compose.yml ps"
echo "Directus:        docker compose --env-file $DIRECTUS_ENV_FILE -f $ROOT_DIR/directus/docker-compose.yml ps"
