#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SOURCE_DIR="$ROOT_DIR/directus/extensions"
RUNTIME_DIR="$ROOT_DIR/directus/runtime-extensions"

if ! command -v npm >/dev/null 2>&1; then
  echo "npm is required to build Directus extensions." >&2
  exit 1
fi

build_extension() {
  local package_dir="$1"
  local runtime_name="$2"

  if [[ ! -f "$package_dir/package.json" ]]; then
    echo "Missing package.json in $package_dir" >&2
    exit 1
  fi

  if [[ -f "$package_dir/package-lock.json" ]]; then
    npm ci --prefix "$package_dir"
  else
    npm install --prefix "$package_dir"
  fi

  npm run build --prefix "$package_dir"

  local runtime_target="$RUNTIME_DIR/$runtime_name"
  mkdir -p "$runtime_target"
  rm -rf "$runtime_target/dist"
  cp "$package_dir/package.json" "$runtime_target/package.json"
  cp -R "$package_dir/dist" "$runtime_target/dist"
}

rm -rf "$RUNTIME_DIR"
mkdir -p "$RUNTIME_DIR"

build_extension "$SOURCE_DIR/directus-endpoint-storefront-ops" "directus-endpoint-storefront-ops"
build_extension "$SOURCE_DIR/directus-module-storefront-ops" "directus-module-storefront-ops"

echo "Directus runtime extensions built into $RUNTIME_DIR"
