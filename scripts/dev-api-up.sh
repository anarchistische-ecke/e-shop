#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SPRING_PROFILE="${SPRING_PROFILE:-dev}"
DIRECTUS_ENV_FILE="$ROOT_DIR/directus/.env"

# shellcheck source=scripts/lib/env-file.sh
source "$ROOT_DIR/scripts/lib/env-file.sh"

cd "$ROOT_DIR"

if [[ -f "$DIRECTUS_ENV_FILE" ]]; then
  load_env_file "$DIRECTUS_ENV_FILE"
fi

export DIRECTUS_BRIDGE_TOKEN="${DIRECTUS_BRIDGE_TOKEN:-local-directus-bridge-token}"

# Reuse the local Directus/MinIO stack for backend-owned catalogue media when
# explicit storage variables are not already set on the host.
export YANDEX_STORAGE_BUCKET="${YANDEX_STORAGE_BUCKET:-${DIRECTUS_STORAGE_S3_BUCKET:-directus}}"
export YANDEX_STORAGE_KEY="${YANDEX_STORAGE_KEY:-${DIRECTUS_STORAGE_S3_KEY:-minioadmin}}"
export YANDEX_STORAGE_SECRET="${YANDEX_STORAGE_SECRET:-${DIRECTUS_STORAGE_S3_SECRET:-minioadmin123}}"
export YANDEX_STORAGE_ENDPOINT="${YANDEX_STORAGE_ENDPOINT:-http://localhost:9000}"
export YANDEX_STORAGE_PUBLIC_BASE_URL="${YANDEX_STORAGE_PUBLIC_BASE_URL:-http://localhost:9000/${YANDEX_STORAGE_BUCKET}}"

echo "Repairing legacy local database schema if needed..."
"$ROOT_DIR/scripts/dev-db-repair.sh"

echo "Building API and module dependencies..."
./mvnw -pl api -am package -DskipTests

JAR_PATH="$(find "$ROOT_DIR/api/target" -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' | head -n 1)"

if [[ -z "$JAR_PATH" ]]; then
  echo "Unable to find packaged API jar in $ROOT_DIR/api/target" >&2
  exit 1
fi

echo "Starting API on profile $SPRING_PROFILE..."
exec java -jar "$JAR_PATH" --spring.profiles.active="$SPRING_PROFILE" "$@"
