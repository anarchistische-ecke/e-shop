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
KEYCLOAK_YANDEX_IDP_ALIAS="${KEYCLOAK_YANDEX_IDP_ALIAS:-yandex}"
KEYCLOAK_YANDEX_FIRST_BROKER_FLOW_ALIAS="${KEYCLOAK_YANDEX_FIRST_BROKER_FLOW_ALIAS:-yandex seamless first broker login}"
KEYCLOAK_YANDEX_TRUST_EMAIL="${KEYCLOAK_YANDEX_TRUST_EMAIL:-true}"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

urlencode_path_segment() {
  python3 - "$1" <<'PY'
import sys
import urllib.parse

print(urllib.parse.quote(sys.argv[1], safe=""))
PY
}

require_command curl
require_command python3

require_env KEYCLOAK_ADMIN_USERNAME "Keycloak admin credentials"
require_env KEYCLOAK_ADMIN_PASSWORD "Keycloak admin credentials"

case "$KEYCLOAK_YANDEX_TRUST_EMAIL" in
  true|false)
    ;;
  *)
    echo "KEYCLOAK_YANDEX_TRUST_EMAIL must be true or false." >&2
    exit 1
    ;;
esac

if [[ "$KEYCLOAK_YANDEX_TRUST_EMAIL" != "true" ]]; then
  cat >&2 <<'EOF'
Refusing to enable seamless Yandex linking while KEYCLOAK_YANDEX_TRUST_EMAIL is not true.
Automatic account linking by matching email is only safe when the external provider's email claim is trusted.
EOF
  exit 1
fi

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

KEYCLOAK_REALM_PATH="$(urlencode_path_segment "$KEYCLOAK_REALM")"
KEYCLOAK_ADMIN_REALM_PATH="$(urlencode_path_segment "$KEYCLOAK_ADMIN_REALM")"
KEYCLOAK_YANDEX_IDP_ALIAS_PATH="$(urlencode_path_segment "$KEYCLOAK_YANDEX_IDP_ALIAS")"
KEYCLOAK_YANDEX_FIRST_BROKER_FLOW_ALIAS_PATH="$(urlencode_path_segment "$KEYCLOAK_YANDEX_FIRST_BROKER_FLOW_ALIAS")"

admin_api_url() {
  printf '%s/admin/realms/%s%s' "$KEYCLOAK_BASE_URL" "$KEYCLOAK_REALM_PATH" "$1"
}

wait_for_keycloak

TOKEN_JSON="$TMP_DIR/token.json"
if ! curl -fsS -X POST "${KEYCLOAK_BASE_URL}/realms/${KEYCLOAK_ADMIN_REALM_PATH}/protocol/openid-connect/token" \
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

api_get_status() {
  curl -sS -H "Authorization: Bearer ${ADMIN_TOKEN}" "$1" -o "$2" -w '%{http_code}'
}

api_post_file() {
  curl -fsS -X POST "$1" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    -H 'Content-Type: application/json' \
    --data-binary "@$2" >/dev/null
}

api_put_file() {
  curl -fsS -X PUT "$1" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    -H 'Content-Type: application/json' \
    --data-binary "@$2" >/dev/null
}

api_delete() {
  curl -fsS -X DELETE "$1" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" >/dev/null
}

FLOWS_JSON="$TMP_DIR/flows.json"
FLOW_PAYLOAD="$TMP_DIR/flow.json"
api_get "$(admin_api_url '/authentication/flows')" "$FLOWS_JSON"

FLOW_STATE="$(
  python3 - "$FLOWS_JSON" "$KEYCLOAK_YANDEX_FIRST_BROKER_FLOW_ALIAS" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    flows = json.load(handle)

alias = sys.argv[2]
flow = next((item for item in flows if item.get("alias") == alias), None)
if flow is None:
    print("missing")
elif flow.get("builtIn"):
    print("built-in")
elif not flow.get("topLevel", False):
    print("not-top-level")
else:
    print("ready")
PY
)"

case "$FLOW_STATE" in
  missing)
    python3 - "$FLOW_PAYLOAD" "$KEYCLOAK_YANDEX_FIRST_BROKER_FLOW_ALIAS" <<'PY'
import json
import sys

payload = {
    "alias": sys.argv[2],
    "description": "Seamless Yandex social login: create unique users or auto-link trusted matching emails.",
    "providerId": "basic-flow",
    "topLevel": True,
    "builtIn": False,
}

with open(sys.argv[1], "w", encoding="utf-8") as handle:
    json.dump(payload, handle, ensure_ascii=False, separators=(",", ":"))
PY
    api_post_file "$(admin_api_url '/authentication/flows')" "$FLOW_PAYLOAD"
    ;;
  ready)
    ;;
  built-in)
    echo "Refusing to modify built-in authentication flow '${KEYCLOAK_YANDEX_FIRST_BROKER_FLOW_ALIAS}'." >&2
    exit 1
    ;;
  not-top-level)
    echo "Authentication flow '${KEYCLOAK_YANDEX_FIRST_BROKER_FLOW_ALIAS}' exists but is not top-level." >&2
    exit 1
    ;;
  *)
    echo "Unexpected flow state '${FLOW_STATE}'." >&2
    exit 1
    ;;
esac

EXECUTIONS_JSON="$TMP_DIR/flow.executions.before.json"
api_get "$(admin_api_url "/authentication/flows/${KEYCLOAK_YANDEX_FIRST_BROKER_FLOW_ALIAS_PATH}/executions")" "$EXECUTIONS_JSON"

python3 - "$EXECUTIONS_JSON" "$TMP_DIR/execution-ids.txt" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    executions = json.load(handle)

with open(sys.argv[2], "w", encoding="utf-8") as handle:
    for execution in executions:
        execution_id = execution.get("id")
        if execution_id:
            handle.write(execution_id + "\n")
PY

while IFS= read -r execution_id; do
  [[ -z "$execution_id" ]] && continue
  api_delete "$(admin_api_url "/authentication/executions/${execution_id}")"
done <"$TMP_DIR/execution-ids.txt"

for provider in idp-create-user-if-unique idp-auto-link; do
  EXECUTION_PAYLOAD="$TMP_DIR/execution-${provider}.json"
  python3 - "$EXECUTION_PAYLOAD" "$provider" <<'PY'
import json
import sys

with open(sys.argv[1], "w", encoding="utf-8") as handle:
    json.dump({"provider": sys.argv[2]}, handle, ensure_ascii=False, separators=(",", ":"))
PY
  api_post_file "$(admin_api_url "/authentication/flows/${KEYCLOAK_YANDEX_FIRST_BROKER_FLOW_ALIAS_PATH}/executions/execution")" "$EXECUTION_PAYLOAD"
done

EXECUTIONS_UPDATED_JSON="$TMP_DIR/flow.executions.after.json"
api_get "$(admin_api_url "/authentication/flows/${KEYCLOAK_YANDEX_FIRST_BROKER_FLOW_ALIAS_PATH}/executions")" "$EXECUTIONS_UPDATED_JSON"

python3 - "$EXECUTIONS_UPDATED_JSON" "$TMP_DIR" <<'PY'
import json
import os
import sys

desired = {"idp-create-user-if-unique", "idp-auto-link"}

with open(sys.argv[1], encoding="utf-8") as handle:
    executions = json.load(handle)

providers = {execution.get("providerId") for execution in executions}
missing = sorted(desired - providers)
extra = sorted(provider for provider in providers - desired if provider)
if missing or extra:
    raise SystemExit(f"Unexpected first broker flow executions. Missing={missing}; extra={extra}")

for execution in executions:
    provider = execution.get("providerId")
    if provider in desired:
        execution["requirement"] = "ALTERNATIVE"
        target = os.path.join(sys.argv[2], f"requirement-{provider}.json")
        with open(target, "w", encoding="utf-8") as handle:
            json.dump(execution, handle, ensure_ascii=False, separators=(",", ":"))
PY

for provider in idp-create-user-if-unique idp-auto-link; do
  api_put_file \
    "$(admin_api_url "/authentication/flows/${KEYCLOAK_YANDEX_FIRST_BROKER_FLOW_ALIAS_PATH}/executions")" \
    "$TMP_DIR/requirement-${provider}.json"
done

IDP_JSON="$TMP_DIR/idp.json"
IDP_STATUS="$(
  api_get_status "$(admin_api_url "/identity-provider/instances/${KEYCLOAK_YANDEX_IDP_ALIAS_PATH}")" "$IDP_JSON"
)"

if [[ "$IDP_STATUS" == "404" ]]; then
  IDP_LIST_JSON="$TMP_DIR/idps.json"
  api_get "$(admin_api_url '/identity-provider/instances')" "$IDP_LIST_JSON"
  echo "Could not find Keycloak identity provider alias '${KEYCLOAK_YANDEX_IDP_ALIAS}' in realm '${KEYCLOAK_REALM}'." >&2
  echo "Available identity provider aliases:" >&2
  python3 - "$IDP_LIST_JSON" <<'PY' >&2
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    providers = json.load(handle)

for provider in providers:
    print(f"- {provider.get('alias', '')}")
PY
  exit 1
fi

if [[ "$IDP_STATUS" != 2* ]]; then
  echo "Failed to read Keycloak identity provider '${KEYCLOAK_YANDEX_IDP_ALIAS}' (HTTP ${IDP_STATUS})." >&2
  exit 1
fi

IDP_UPDATED_JSON="$TMP_DIR/idp.updated.json"
python3 - "$IDP_JSON" "$IDP_UPDATED_JSON" "$KEYCLOAK_YANDEX_FIRST_BROKER_FLOW_ALIAS" "$KEYCLOAK_YANDEX_TRUST_EMAIL" <<'PY'
import json
import sys

source, target, flow_alias, trust_email = sys.argv[1:5]

with open(source, encoding="utf-8") as handle:
    provider = json.load(handle)

provider["firstBrokerLoginFlowAlias"] = flow_alias
provider["trustEmail"] = trust_email == "true"

if not isinstance(provider.get("config"), dict):
    provider["config"] = {}

with open(target, "w", encoding="utf-8") as handle:
    json.dump(provider, handle, ensure_ascii=False, separators=(",", ":"))
PY

api_put_file "$(admin_api_url "/identity-provider/instances/${KEYCLOAK_YANDEX_IDP_ALIAS_PATH}")" "$IDP_UPDATED_JSON"

cat <<EOF
Keycloak Yandex seamless login policy applied.
Realm: ${KEYCLOAK_REALM}
Yandex identity provider alias: ${KEYCLOAK_YANDEX_IDP_ALIAS}
First broker login flow: ${KEYCLOAK_YANDEX_FIRST_BROKER_FLOW_ALIAS}
Flow executions: idp-create-user-if-unique=ALTERNATIVE, idp-auto-link=ALTERNATIVE
Yandex trustEmail: ${KEYCLOAK_YANDEX_TRUST_EMAIL}

Important:
- This removes the email confirmation/linking screen for first-time Yandex broker logins when the Yandex email matches an existing Keycloak user.
- Keep it enabled only while Yandex email claims are trusted for account ownership.
EOF
