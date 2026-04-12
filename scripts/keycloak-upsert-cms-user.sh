#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
KEYCLOAK_ENV_FILE="$ROOT_DIR/keycloak/.env"
KEYCLOAK_BASE_URL="${KEYCLOAK_BASE_URL:-http://localhost:8081}"
KEYCLOAK_REALM="${KEYCLOAK_REALM:-cozyhome}"
MAPPED_ROLES="admin manager publisher"

if [[ ! -f "$KEYCLOAK_ENV_FILE" ]]; then
  echo "Missing $KEYCLOAK_ENV_FILE" >&2
  exit 1
fi

set -a
. "$KEYCLOAK_ENV_FILE"
set +a

EMAIL=""
PASSWORD=""
ROLE=""
FIRST_NAME="CMS"
LAST_NAME="User"
TEMPORARY_PASSWORD="false"

usage() {
  cat <<'EOF'
Usage:
  ./scripts/keycloak-upsert-cms-user.sh \
    --email editor@example.com \
    --password 'ChangeMe123!' \
    --role manager \
    [--first-name Editor] \
    [--last-name Team] \
    [--temporary-password]

Allowed roles:
  manager   -> Directus CMS Editor
  publisher -> Directus CMS Publisher
  admin     -> Directus CMS Administrator
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --email)
      EMAIL="${2:-}"
      shift 2
      ;;
    --password)
      PASSWORD="${2:-}"
      shift 2
      ;;
    --role)
      ROLE="${2:-}"
      shift 2
      ;;
    --first-name)
      FIRST_NAME="${2:-}"
      shift 2
      ;;
    --last-name)
      LAST_NAME="${2:-}"
      shift 2
      ;;
    --temporary-password)
      TEMPORARY_PASSWORD="true"
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

if [[ -z "$EMAIL" || -z "$PASSWORD" || -z "$ROLE" ]]; then
  usage >&2
  exit 1
fi

case "$ROLE" in
  admin|manager|publisher)
    ;;
  *)
    echo "Unsupported CMS role '$ROLE'. Use one of: admin, manager, publisher." >&2
    exit 1
    ;;
esac

directus_role_label() {
  case "$1" in
    admin) printf 'CMS Administrator' ;;
    manager) printf 'CMS Editor' ;;
    publisher) printf 'CMS Publisher' ;;
    *) printf 'unknown' ;;
  esac
}

json_escape() {
  printf '%s' "$1" | python3 -c 'import json,sys; print(json.dumps(sys.stdin.read()))'
}

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

wait_for_keycloak

ADMIN_TOKEN="$(
  curl -fsS -X POST "${KEYCLOAK_BASE_URL}/realms/master/protocol/openid-connect/token" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode 'client_id=admin-cli' \
    --data-urlencode 'grant_type=password' \
    --data-urlencode "username=${KEYCLOAK_BOOTSTRAP_ADMIN_USERNAME}" \
    --data-urlencode "password=${KEYCLOAK_BOOTSTRAP_ADMIN_PASSWORD}" \
  | jq -r '.access_token'
)"

if [[ -z "$ADMIN_TOKEN" || "$ADMIN_TOKEN" == "null" ]]; then
  echo "Failed to obtain Keycloak admin token." >&2
  exit 1
fi

api_get() {
  curl -fsS -H "Authorization: Bearer ${ADMIN_TOKEN}" "$1"
}

api_post_json() {
  local url="$1"
  local payload="$2"
  curl -fsS -X POST "$url" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    -H 'Content-Type: application/json' \
    -d "$payload" >/dev/null
}

api_put_json() {
  local url="$1"
  local payload="$2"
  curl -fsS -X PUT "$url" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    -H 'Content-Type: application/json' \
    -d "$payload" >/dev/null
}

api_delete_json() {
  local url="$1"
  local payload="$2"
  curl -fsS -X DELETE "$url" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    -H 'Content-Type: application/json' \
    -d "$payload" >/dev/null
}

ROLE_HTTP_STATUS="$(
  curl -s -o /tmp/keycloak-role.json -w '%{http_code}' \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    "${KEYCLOAK_BASE_URL}/admin/realms/${KEYCLOAK_REALM}/roles/${ROLE}"
)"

if [[ "$ROLE_HTTP_STATUS" == "404" ]]; then
  api_post_json "${KEYCLOAK_BASE_URL}/admin/realms/${KEYCLOAK_REALM}/roles" "{\"name\": $(json_escape "$ROLE")}"
  ROLE_HTTP_STATUS="$(
    curl -s -o /tmp/keycloak-role.json -w '%{http_code}' \
      -H "Authorization: Bearer ${ADMIN_TOKEN}" \
      "${KEYCLOAK_BASE_URL}/admin/realms/${KEYCLOAK_REALM}/roles/${ROLE}"
  )"
fi

if [[ "$ROLE_HTTP_STATUS" != "200" ]]; then
  echo "Failed to resolve Keycloak realm role '${ROLE}' in realm '${KEYCLOAK_REALM}'." >&2
  exit 1
fi

ROLE_JSON="$(cat /tmp/keycloak-role.json)"

USER_JSON="$(
  curl -fsS -G "${KEYCLOAK_BASE_URL}/admin/realms/${KEYCLOAK_REALM}/users" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    --data-urlencode "username=${EMAIL}"
)"
USER_ID="$(printf '%s' "$USER_JSON" | jq -r '.[0].id // ""')"

USER_PAYLOAD="$(
  cat <<JSON
{
  "username": $(json_escape "$EMAIL"),
  "email": $(json_escape "$EMAIL"),
  "enabled": true,
  "emailVerified": true,
  "firstName": $(json_escape "$FIRST_NAME"),
  "lastName": $(json_escape "$LAST_NAME")
}
JSON
)"

if [[ -z "$USER_ID" ]]; then
  api_post_json "${KEYCLOAK_BASE_URL}/admin/realms/${KEYCLOAK_REALM}/users" "$USER_PAYLOAD"
  USER_JSON="$(
    curl -fsS -G "${KEYCLOAK_BASE_URL}/admin/realms/${KEYCLOAK_REALM}/users" \
      -H "Authorization: Bearer ${ADMIN_TOKEN}" \
      --data-urlencode "username=${EMAIL}"
  )"
  USER_ID="$(printf '%s' "$USER_JSON" | jq -r '.[0].id // ""')"
fi

if [[ -z "$USER_ID" ]]; then
  echo "Failed to resolve Keycloak user id for ${EMAIL}." >&2
  exit 1
fi

api_put_json "${KEYCLOAK_BASE_URL}/admin/realms/${KEYCLOAK_REALM}/users/${USER_ID}" "$USER_PAYLOAD"

PASSWORD_PAYLOAD="$(
  cat <<JSON
{
  "type": "password",
  "value": $(json_escape "$PASSWORD"),
  "temporary": ${TEMPORARY_PASSWORD}
}
JSON
)"
api_put_json "${KEYCLOAK_BASE_URL}/admin/realms/${KEYCLOAK_REALM}/users/${USER_ID}/reset-password" "$PASSWORD_PAYLOAD"

CURRENT_REALM_ROLES="$(api_get "${KEYCLOAK_BASE_URL}/admin/realms/${KEYCLOAK_REALM}/users/${USER_ID}/role-mappings/realm")"
REMOVE_PAYLOAD="$(
  printf '%s' "$CURRENT_REALM_ROLES" \
    | jq --arg selected "$ROLE" '[.[] | select((.name == "admin" or .name == "manager" or .name == "publisher") and .name != $selected)]'
)"

if [[ "$REMOVE_PAYLOAD" != "[]" ]]; then
  api_delete_json "${KEYCLOAK_BASE_URL}/admin/realms/${KEYCLOAK_REALM}/users/${USER_ID}/role-mappings/realm" "$REMOVE_PAYLOAD"
fi

CURRENT_REALM_ROLES="$(api_get "${KEYCLOAK_BASE_URL}/admin/realms/${KEYCLOAK_REALM}/users/${USER_ID}/role-mappings/realm")"
HAS_ROLE="$(printf '%s' "$CURRENT_REALM_ROLES" | jq --arg role "$ROLE" 'any(.[]; .name == $role)')"

if [[ "$HAS_ROLE" != "true" ]]; then
  api_post_json "${KEYCLOAK_BASE_URL}/admin/realms/${KEYCLOAK_REALM}/users/${USER_ID}/role-mappings/realm" "[${ROLE_JSON}]"
fi

cat <<EOF
Keycloak CMS user upserted.
Realm: ${KEYCLOAK_REALM}
Email: ${EMAIL}
Assigned Keycloak role: ${ROLE}
Expected Directus role on first SSO login: $(directus_role_label "$ROLE")

Next step:
1. Open ${KEYCLOAK_BASE_URL%:8081}:8055
2. Click the Keycloak login button
3. Sign in as ${EMAIL}

Important:
- Keep exactly one mapped Keycloak realm role among: ${MAPPED_ROLES}
- Directus creates or updates the user on first SSO login
EOF
