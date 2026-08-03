#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"

# shellcheck source=scripts/lib/env-file.sh
source "$ROOT_DIR/scripts/lib/env-file.sh"

usage() {
  cat <<'EOF'
Usage:
  ./scripts/directus-service-secrets-bootstrap.sh [--env-file <path>]

Generates and persists the server-side Directus reader and preview secrets when
they are absent. Existing non-empty values are preserved.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file)
      ENV_FILE="$(resolve_env_file_path "$2")"
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

unset DIRECTUS_STATIC_TOKEN DIRECTUS_PREVIEW_TOKEN CMS_PREVIEW_SECRET
load_env_file "$ENV_FILE"

generate_secret() {
  local value

  value="$(od -An -N32 -tx1 /dev/urandom | tr -d '[:space:]')"
  if [[ ! "$value" =~ ^[0-9a-f]{64}$ ]]; then
    echo "Could not generate a 256-bit secret." >&2
    return 1
  fi

  printf '%s' "$value"
}

persist_secret() {
  local key="$1"
  local current_value="${!key:-}"
  local generated_value temp_file

  if [[ -n "$current_value" ]]; then
    echo "${key} is already provisioned."
    return 0
  fi

  generated_value="$(generate_secret)"
  temp_file="$(mktemp "${ENV_FILE}.tmp.XXXXXX")"
  trap 'rm -f "$temp_file"' RETURN

  awk -v key="$key" -v value="$generated_value" '
    BEGIN { replaced = 0 }
    $0 ~ "^[[:space:]]*" key "[[:space:]]*=" {
      if (!replaced) {
        print key "=" value
        replaced = 1
      }
      next
    }
    { print }
    END {
      if (!replaced) {
        print key "=" value
      }
    }
  ' "$ENV_FILE" > "$temp_file"

  chmod 600 "$temp_file"
  mv "$temp_file" "$ENV_FILE"
  trap - RETURN
  printf -v "$key" '%s' "$generated_value"
  export "$key"
  echo "Provisioned ${key}."
}

persist_secret DIRECTUS_STATIC_TOKEN
persist_secret DIRECTUS_PREVIEW_TOKEN
persist_secret CMS_PREVIEW_SECRET

chmod 600 "$ENV_FILE"
echo "Directus server-side service secrets are ready."
