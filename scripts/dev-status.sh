#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

check_url() {
  local label="$1"
  local url="$2"

  if curl -fsS "$url" >/dev/null 2>&1; then
    echo "[ok]   $label -> $url"
  else
    echo "[fail] $label -> $url"
  fi
}

echo "Docker services"
docker compose -f "$ROOT_DIR/docker-compose.yml" ps
docker compose --env-file "$ROOT_DIR/keycloak/.env" -f "$ROOT_DIR/keycloak/docker-compose.yml" ps
docker compose --env-file "$ROOT_DIR/directus/.env" -f "$ROOT_DIR/directus/docker-compose.yml" ps

echo
echo "HTTP checks"
check_url "Backend Redis health" "http://localhost:8080/health/redis"
check_url "Backend categories" "http://localhost:8080/categories"
check_url "Backend products" "http://localhost:8080/products"
check_url "Keycloak discovery" "http://localhost:8081/realms/cozyhome/.well-known/openid-configuration"
check_url "Directus health" "http://localhost:8055/server/health"
check_url "MinIO health" "http://localhost:9000/minio/health/live"
check_url "Storefront root" "http://localhost:3000"
