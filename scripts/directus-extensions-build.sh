#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SOURCE_DIR="$ROOT_DIR/directus/extensions"
RUNTIME_DIR="$ROOT_DIR/directus/runtime-extensions"
CHECK_MODE=false

usage() {
  cat <<'EOF'
Usage:
  ./scripts/directus-extensions-build.sh [--check]

Builds Directus extension packages and writes the deployable runtime bundle to
directus/runtime-extensions.

Options:
  --check   Build into a temporary directory and fail if committed runtime
            extensions are not up to date.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --check)
      CHECK_MODE=true
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

if ! command -v npm >/dev/null 2>&1; then
  echo "npm is required to build Directus extensions." >&2
  exit 1
fi

if [[ "$CHECK_MODE" == "true" ]]; then
  CHECK_DIR="$(mktemp -d)"
  trap 'rm -rf "$CHECK_DIR"' EXIT
  RUNTIME_DIR="$CHECK_DIR/runtime-extensions"
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
build_extension "$SOURCE_DIR/directus-panel-storefront-ops-launcher" "directus-panel-storefront-ops-launcher"

if [[ "$CHECK_MODE" == "true" ]]; then
  diff_file="$(mktemp)"
  if diff -ruN "$ROOT_DIR/directus/runtime-extensions" "$RUNTIME_DIR" >"$diff_file"; then
    rm -f "$diff_file"
    echo "Committed Directus runtime extensions are up to date."
    exit 0
  fi

  echo "Committed Directus runtime extensions are out of date." >&2
  echo "Run ./scripts/directus-extensions-build.sh, then commit directus/runtime-extensions." >&2
  echo "Changed generated files:" >&2
  git -C "$ROOT_DIR" diff --no-index --name-only "$ROOT_DIR/directus/runtime-extensions" "$RUNTIME_DIR" 2>/dev/null \
    | sed "s#^$ROOT_DIR/directus/runtime-extensions/#  directus/runtime-extensions/#;s#^$RUNTIME_DIR/#  directus/runtime-extensions/#" \
    | sort -u >&2 || true
  rm -f "$diff_file"
  exit 1
fi

echo "Directus runtime extensions built into $RUNTIME_DIR"
