#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"
MODE="check"
BACKUP_ROOT="$ROOT_DIR/.deploy-state/media-upload-backups"

# shellcheck source=scripts/lib/env-file.sh
source "$ROOT_DIR/scripts/lib/env-file.sh"

usage() {
  echo "Usage: $0 [--env-file <path>] [--check|--apply] [--backup-root <path>]"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file) ENV_FILE="$(resolve_env_file_path "$2")"; shift 2 ;;
    --check) MODE="check"; shift ;;
    --apply) MODE="apply"; shift ;;
    --backup-root) BACKUP_ROOT="$(resolve_env_file_path "$2")"; shift 2 ;;
    --help|-h) usage; exit 0 ;;
    *) echo "Unsupported argument: $1" >&2; usage >&2; exit 1 ;;
  esac
done

load_env_file "$ENV_FILE"
require_env YANDEX_STORAGE_BUCKET "$ENV_FILE"
require_env YANDEX_STORAGE_KEY "$ENV_FILE"
require_env YANDEX_STORAGE_SECRET "$ENV_FILE"
require_env MEDIA_DERIVATIVES_BUCKET "$ENV_FILE"

if [[ "$YANDEX_STORAGE_BUCKET" == "$MEDIA_DERIVATIVES_BUCKET" ]]; then
  echo "Original and derivative buckets must be different." >&2
  exit 1
fi

args=(
  python3 "$ROOT_DIR/scripts/media-upload-storage-config.py"
  --bucket "$YANDEX_STORAGE_BUCKET"
  --endpoint "${YANDEX_STORAGE_ENDPOINT:-https://storage.yandexcloud.net}"
  --pending-prefix "${CATALOGUE_MEDIA_PENDING_PREFIX:-media-upload-pending}"
)

if [[ "$MODE" == "apply" ]]; then
  stamp="$(date -u +%Y%m%dT%H%M%SZ)"
  backup_dir="$BACKUP_ROOT/$stamp"
  mkdir -p "$backup_dir"
  chmod 700 "$backup_dir"
  cp "$ENV_FILE" "$backup_dir/production.env"
  chmod 600 "$backup_dir/production.env"
  yc_bin="${YC_BIN:-$HOME/yandex-cloud/bin/yc}"
  "$yc_bin" storage bucket get "$YANDEX_STORAGE_BUCKET" --full --format json >"$backup_dir/original-bucket.json"
  "$yc_bin" storage bucket get "$MEDIA_DERIVATIVES_BUCKET" --full --format json >"$backup_dir/derivative-bucket.json"
  "$yc_bin" cdn resource list --format json >"$backup_dir/cdn-resources.json"
  "${args[@]}" --apply --backup-dir "$backup_dir"
  set_env_value() {
    local key="$1"
    local value="$2"
    local temp
    temp="$(mktemp)"
    awk -v key="$key" -v value="$value" '
      BEGIN { updated = 0 }
      $0 ~ "^[[:space:]]*" key "=" {
        print key "=" value
        updated = 1
        next
      }
      { print }
      END {
        if (!updated) print key "=" value
      }
    ' "$ENV_FILE" >"$temp"
    chmod --reference="$ENV_FILE" "$temp" 2>/dev/null || chmod 600 "$temp"
    mv "$temp" "$ENV_FILE"
  }
  set_env_value CATALOGUE_MEDIA_UPLOAD_ENABLED false
  set_env_value CATALOGUE_MEDIA_PROCESSOR_ENABLED true
  set_env_value CATALOGUE_MEDIA_MAX_FILE_SIZE 100MB
  set_env_value CATALOGUE_MEDIA_MAX_PIXELS 100000000
  set_env_value CONTENT_SECURITY_POLICY_DIRECTIVES__CONNECT_SRC "\"'self' https://api.yug-postel.ru https://storage.yandexcloud.net https://*.storage.yandexcloud.net wss://cms.yug-postel.ru\""
  echo "Backup created: $backup_dir"
else
  "${args[@]}" --check
fi
