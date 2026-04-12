#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
DIRECTUS_DIR="$ROOT_DIR/directus"
ENV_FILE="$DIRECTUS_DIR/.env"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing $ENV_FILE. Create it from $DIRECTUS_DIR/.env.example first." >&2
  exit 1
fi

set -a
. "$ENV_FILE"
set +a

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

IMAGE_PATH="$TMP_DIR/directus-storage-test.png"

base64 -d > "$IMAGE_PATH" <<'EOF'
iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aF6kAAAAASUVORK5CYII=
EOF

LOGIN_RESPONSE="$(curl -fsS -X POST "${DIRECTUS_PUBLIC_URL}/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"${DIRECTUS_ADMIN_EMAIL}\",\"password\":\"${DIRECTUS_ADMIN_PASSWORD}\"}")"

ACCESS_TOKEN="$(node -e 'const data = JSON.parse(process.argv[1]); console.log(data.data.access_token);' "$LOGIN_RESPONSE")"

UPLOAD_RESPONSE="$(curl -fsS -X POST "${DIRECTUS_PUBLIC_URL}/files" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -F "file=@${IMAGE_PATH};type=image/png")"

FILE_ID="$(node -e 'const data = JSON.parse(process.argv[1]); console.log(data.data.id);' "$UPLOAD_RESPONSE")"
FILE_STORAGE="$(node -e 'const data = JSON.parse(process.argv[1]); console.log(data.data.storage);' "$UPLOAD_RESPONSE")"
FILE_DISK_NAME="$(node -e 'const data = JSON.parse(process.argv[1]); console.log(data.data.filename_disk);' "$UPLOAD_RESPONSE")"

if [[ "$FILE_STORAGE" != "s3" ]]; then
  echo "Expected Directus storage adapter 's3', got '$FILE_STORAGE'." >&2
  exit 1
fi

ASSET_STATUS="$(curl -s -o /dev/null -w '%{http_code}' \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  "${DIRECTUS_PUBLIC_URL}/assets/${FILE_ID}")"

if [[ "$ASSET_STATUS" != "200" ]]; then
  echo "Directus asset route returned HTTP ${ASSET_STATUS}." >&2
  exit 1
fi

RAW_URL="${DIRECTUS_STORAGE_PUBLIC_BASE_URL}/${FILE_DISK_NAME}"
RAW_STATUS="$(curl -s -o /dev/null -w '%{http_code}' "$RAW_URL")"

if [[ "$RAW_STATUS" != "200" ]]; then
  echo "Raw storage URL returned HTTP ${RAW_STATUS}: ${RAW_URL}" >&2
  exit 1
fi

echo "Directus storage smoke test passed."
echo "File ID: ${FILE_ID}"
echo "Storage adapter: ${FILE_STORAGE}"
echo "Raw URL: ${RAW_URL}"
