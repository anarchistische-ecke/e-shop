#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

echo "Applying committed Directus schema snapshot..."
"$ROOT_DIR/scripts/directus-schema-apply.sh" "$@"
