#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

docker compose --env-file "$ROOT_DIR/directus/.env" -f "$ROOT_DIR/directus/docker-compose.yml" down
docker compose --env-file "$ROOT_DIR/keycloak/.env" -f "$ROOT_DIR/keycloak/docker-compose.yml" down
docker compose -f "$ROOT_DIR/docker-compose.yml" down
