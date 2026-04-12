#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SPRING_PROFILE="${SPRING_PROFILE:-dev}"

cd "$ROOT_DIR"

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
