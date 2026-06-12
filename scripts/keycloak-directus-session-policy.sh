#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
KEYCLOAK_ENV_FILE="${KEYCLOAK_ENV_FILE:-$ROOT_DIR/keycloak/.env}"
TMP_DIR="$(mktemp -d)"

cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

# shellcheck source=scripts/lib/env-file.sh
source "$ROOT_DIR/scripts/lib/env-file.sh"

if [[ -f "$KEYCLOAK_ENV_FILE" ]]; then
  load_env_file "$KEYCLOAK_ENV_FILE"
fi

KEYCLOAK_BASE_URL="${KEYCLOAK_BASE_URL:-http://localhost:8081}"
KEYCLOAK_REALM="${KEYCLOAK_REALM:-cozyhome}"
KEYCLOAK_ADMIN_REALM="${KEYCLOAK_ADMIN_REALM:-master}"
KEYCLOAK_ADMIN_CLIENT_ID="${KEYCLOAK_ADMIN_CLIENT_ID:-admin-cli}"
KEYCLOAK_ADMIN_USERNAME="${KEYCLOAK_ADMIN_USERNAME:-${KEYCLOAK_BOOTSTRAP_ADMIN_USERNAME:-${KC_BOOTSTRAP_ADMIN_USERNAME:-}}}"
KEYCLOAK_ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-${KEYCLOAK_BOOTSTRAP_ADMIN_PASSWORD:-${KC_BOOTSTRAP_ADMIN_PASSWORD:-}}}"
KEYCLOAK_DIRECTUS_CLIENT_ID="${KEYCLOAK_DIRECTUS_CLIENT_ID:-${DIRECTUS_AUTH_KEYCLOAK_CLIENT_ID:-directus}}"
KEYCLOAK_SSO_SESSION_IDLE_TIMEOUT="${KEYCLOAK_SSO_SESSION_IDLE_TIMEOUT:-28800}"
KEYCLOAK_SSO_SESSION_MAX_LIFESPAN="${KEYCLOAK_SSO_SESSION_MAX_LIFESPAN:-43200}"
KEYCLOAK_SSO_SESSION_IDLE_TIMEOUT_REMEMBER_ME="${KEYCLOAK_SSO_SESSION_IDLE_TIMEOUT_REMEMBER_ME:-2592000}"
KEYCLOAK_SSO_SESSION_MAX_LIFESPAN_REMEMBER_ME="${KEYCLOAK_SSO_SESSION_MAX_LIFESPAN_REMEMBER_ME:-7776000}"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

require_command curl
require_command python3

require_env KEYCLOAK_ADMIN_USERNAME "Keycloak admin credentials"
require_env KEYCLOAK_ADMIN_PASSWORD "Keycloak admin credentials"

wait_for_keycloak() {
  for _ in $(seq 1 60); do
    if curl -fsS "${KEYCLOAK_BASE_URL}/realms/${KEYCLOAK_REALM}/.well-known/openid-configuration" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done

  echo "Timed out waiting for Keycloak at ${KEYCLOAK_BASE_URL}" >&2
  exit 1
}

admin_api_url() {
  printf '%s/admin/realms/%s%s' "$KEYCLOAK_BASE_URL" "$KEYCLOAK_REALM" "$1"
}

wait_for_keycloak

TOKEN_JSON="$TMP_DIR/token.json"
if ! curl -fsS -X POST "${KEYCLOAK_BASE_URL}/realms/${KEYCLOAK_ADMIN_REALM}/protocol/openid-connect/token" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode "client_id=${KEYCLOAK_ADMIN_CLIENT_ID}" \
  --data-urlencode 'grant_type=password' \
  --data-urlencode "username=${KEYCLOAK_ADMIN_USERNAME}" \
  --data-urlencode "password=${KEYCLOAK_ADMIN_PASSWORD}" \
  -o "$TOKEN_JSON"; then
  echo "Failed to obtain Keycloak admin token for ${KEYCLOAK_ADMIN_USERNAME}." >&2
  exit 1
fi

ADMIN_TOKEN="$(
  python3 - "$TOKEN_JSON" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    print(json.load(handle).get("access_token", ""))
PY
)"

if [[ -z "$ADMIN_TOKEN" ]]; then
  echo "Keycloak admin token response did not include access_token." >&2
  exit 1
fi

api_get() {
  curl -fsS -H "Authorization: Bearer ${ADMIN_TOKEN}" "$1" -o "$2"
}

api_put_file() {
  curl -fsS -X PUT "$1" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    -H 'Content-Type: application/json' \
    --data-binary "@$2" >/dev/null
}

REALM_JSON="$TMP_DIR/realm.json"
REALM_UPDATED_JSON="$TMP_DIR/realm.updated.json"
api_get "$(admin_api_url '')" "$REALM_JSON"

python3 - "$REALM_JSON" "$REALM_UPDATED_JSON" \
  "$KEYCLOAK_SSO_SESSION_IDLE_TIMEOUT" \
  "$KEYCLOAK_SSO_SESSION_MAX_LIFESPAN" \
  "$KEYCLOAK_SSO_SESSION_IDLE_TIMEOUT_REMEMBER_ME" \
  "$KEYCLOAK_SSO_SESSION_MAX_LIFESPAN_REMEMBER_ME" <<'PY'
import json
import sys

source, target = sys.argv[1], sys.argv[2]
idle, maximum, remember_idle, remember_maximum = map(int, sys.argv[3:7])

with open(source, encoding="utf-8") as handle:
    realm = json.load(handle)

realm["rememberMe"] = True
realm["ssoSessionIdleTimeout"] = idle
realm["ssoSessionMaxLifespan"] = maximum
realm["ssoSessionIdleTimeoutRememberMe"] = remember_idle
realm["ssoSessionMaxLifespanRememberMe"] = remember_maximum

with open(target, "w", encoding="utf-8") as handle:
    json.dump(realm, handle, ensure_ascii=False, separators=(",", ":"))
PY

api_put_file "$(admin_api_url '')" "$REALM_UPDATED_JSON"

CLIENTS_JSON="$TMP_DIR/clients.json"
curl -fsS -G "$(admin_api_url '/clients')" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  --data-urlencode "clientId=${KEYCLOAK_DIRECTUS_CLIENT_ID}" \
  -o "$CLIENTS_JSON"

CLIENT_UUID="$(
  python3 - "$CLIENTS_JSON" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    clients = json.load(handle)

print(clients[0].get("id", "") if clients else "")
PY
)"

if [[ -z "$CLIENT_UUID" ]]; then
  echo "Could not find Keycloak client '${KEYCLOAK_DIRECTUS_CLIENT_ID}' in realm '${KEYCLOAK_REALM}'." >&2
  exit 1
fi

CLIENT_JSON="$TMP_DIR/client.json"
CLIENT_UPDATED_JSON="$TMP_DIR/client.updated.json"
api_get "$(admin_api_url "/clients/${CLIENT_UUID}")" "$CLIENT_JSON"

python3 - "$CLIENT_JSON" "$CLIENT_UPDATED_JSON" <<'PY'
import json
import sys

source, target = sys.argv[1], sys.argv[2]
session_override_keys = {
    "client.session.idle.timeout",
    "client.session.max.lifespan",
}

with open(source, encoding="utf-8") as handle:
    client = json.load(handle)

attributes = client.get("attributes") or {}
for key in session_override_keys:
    attributes.pop(key, None)

if attributes:
    client["attributes"] = attributes
else:
    client.pop("attributes", None)

with open(target, "w", encoding="utf-8") as handle:
    json.dump(client, handle, ensure_ascii=False, separators=(",", ":"))
PY

api_put_file "$(admin_api_url "/clients/${CLIENT_UUID}")" "$CLIENT_UPDATED_JSON"

cat <<EOF
Keycloak Directus session policy applied.
Realm: ${KEYCLOAK_REALM}
Directus client: ${KEYCLOAK_DIRECTUS_CLIENT_ID}
SSO idle/max: ${KEYCLOAK_SSO_SESSION_IDLE_TIMEOUT}s / ${KEYCLOAK_SSO_SESSION_MAX_LIFESPAN}s
Remember-me idle/max: ${KEYCLOAK_SSO_SESSION_IDLE_TIMEOUT_REMEMBER_ME}s / ${KEYCLOAK_SSO_SESSION_MAX_LIFESPAN_REMEMBER_ME}s
Directus client session overrides removed: client.session.idle.timeout, client.session.max.lifespan
EOF
