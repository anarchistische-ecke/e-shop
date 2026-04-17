#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
DIRECTUS_ENV_FILE="$ROOT_DIR/directus/.env"
KEYCLOAK_ENV_FILE="$ROOT_DIR/keycloak/.env"

if [[ ! -f "$DIRECTUS_ENV_FILE" ]]; then
  echo "Missing $DIRECTUS_ENV_FILE" >&2
  exit 1
fi

if [[ ! -f "$KEYCLOAK_ENV_FILE" ]]; then
  echo "Missing $KEYCLOAK_ENV_FILE" >&2
  exit 1
fi

set -a
. "$DIRECTUS_ENV_FILE"
. "$KEYCLOAK_ENV_FILE"
set +a

DIRECTUS_PUBLIC_URL="${DIRECTUS_PUBLIC_URL:-http://localhost:8055}"
DIRECTUS_DEFAULT_LANGUAGE="${DIRECTUS_DEFAULT_LANGUAGE:-ru-RU}"
DIRECTUS_PROJECT_NAME="${DIRECTUS_PROJECT_NAME:-Магазин}"
DIRECTUS_AUTH_KEYCLOAK_CLIENT_ID="${DIRECTUS_AUTH_KEYCLOAK_CLIENT_ID:-directus}"
DIRECTUS_AUTH_KEYCLOAK_CLIENT_SECRET="${DIRECTUS_AUTH_KEYCLOAK_CLIENT_SECRET:-directus-local-secret}"
LEGACY_DIRECTUS_ADMIN_EMAIL="${LEGACY_DIRECTUS_ADMIN_EMAIL:-admin@example.com}"
DIRECTUS_ROLE_CMS_ADMIN_ID="${DIRECTUS_ROLE_CMS_ADMIN_ID:-4c4cc8d0-9b7f-4d56-84d2-1d64f5f10001}"
DIRECTUS_ROLE_CMS_EDITOR_ID="${DIRECTUS_ROLE_CMS_EDITOR_ID:-4c4cc8d0-9b7f-4d56-84d2-1d64f5f10002}"
DIRECTUS_ROLE_CMS_PUBLISHER_ID="${DIRECTUS_ROLE_CMS_PUBLISHER_ID:-4c4cc8d0-9b7f-4d56-84d2-1d64f5f10003}"
DIRECTUS_ROLE_CATALOGUE_OPERATOR_ID="${DIRECTUS_ROLE_CATALOGUE_OPERATOR_ID:-4c4cc8d0-9b7f-4d56-84d2-1d64f5f10004}"
DIRECTUS_ROLE_INVENTORY_OPERATOR_ID="${DIRECTUS_ROLE_INVENTORY_OPERATOR_ID:-4c4cc8d0-9b7f-4d56-84d2-1d64f5f10005}"
DIRECTUS_POLICY_CMS_ADMIN_ID="${DIRECTUS_POLICY_CMS_ADMIN_ID:-4c4cc8d0-9b7f-4d56-84d2-1d64f5f20001}"
DIRECTUS_POLICY_CMS_EDITOR_ID="${DIRECTUS_POLICY_CMS_EDITOR_ID:-4c4cc8d0-9b7f-4d56-84d2-1d64f5f20002}"
DIRECTUS_POLICY_CMS_PUBLISHER_ID="${DIRECTUS_POLICY_CMS_PUBLISHER_ID:-4c4cc8d0-9b7f-4d56-84d2-1d64f5f20003}"
DIRECTUS_POLICY_CATALOGUE_OPERATOR_ID="${DIRECTUS_POLICY_CATALOGUE_OPERATOR_ID:-4c4cc8d0-9b7f-4d56-84d2-1d64f5f20004}"
DIRECTUS_POLICY_INVENTORY_OPERATOR_ID="${DIRECTUS_POLICY_INVENTORY_OPERATOR_ID:-4c4cc8d0-9b7f-4d56-84d2-1d64f5f20005}"
DIRECTUS_ACCESS_CMS_ADMIN_ID="${DIRECTUS_ACCESS_CMS_ADMIN_ID:-4c4cc8d0-9b7f-4d56-84d2-1d64f5f30001}"
DIRECTUS_ACCESS_CMS_EDITOR_ID="${DIRECTUS_ACCESS_CMS_EDITOR_ID:-4c4cc8d0-9b7f-4d56-84d2-1d64f5f30002}"
DIRECTUS_ACCESS_CMS_PUBLISHER_ID="${DIRECTUS_ACCESS_CMS_PUBLISHER_ID:-4c4cc8d0-9b7f-4d56-84d2-1d64f5f30003}"
DIRECTUS_ACCESS_CATALOGUE_OPERATOR_ID="${DIRECTUS_ACCESS_CATALOGUE_OPERATOR_ID:-4c4cc8d0-9b7f-4d56-84d2-1d64f5f30004}"
DIRECTUS_ACCESS_INVENTORY_OPERATOR_ID="${DIRECTUS_ACCESS_INVENTORY_OPERATOR_ID:-4c4cc8d0-9b7f-4d56-84d2-1d64f5f30005}"
DIRECTUS_DASHBOARD_STOREFRONT_OPS_ID="${DIRECTUS_DASHBOARD_STOREFRONT_OPS_ID:-4c4cc8d0-9b7f-4d56-84d2-1d64f5f40001}"
DIRECTUS_PANEL_STOREFRONT_OPS_LAUNCHER_ID="${DIRECTUS_PANEL_STOREFRONT_OPS_LAUNCHER_ID:-4c4cc8d0-9b7f-4d56-84d2-1d64f5f40002}"
DIRECTUS_CMS_CONTENT_COLLECTIONS="${DIRECTUS_CMS_CONTENT_COLLECTIONS:-site_settings,navigation,navigation_items,page,page_sections,page_section_items,faq,legal_documents,banner,post,product_overlay,category_overlay,catalogue_overlay_block,catalogue_overlay_block_item,storefront_collection,storefront_collection_item}"
DIRECTUS_CMS_PUBLIC_COLLECTIONS="${DIRECTUS_CMS_PUBLIC_COLLECTIONS:-$DIRECTUS_CMS_CONTENT_COLLECTIONS}"
DIRECTUS_CMS_STATUS_FIELD="${DIRECTUS_CMS_STATUS_FIELD:-status}"

EDITOR_CREATE_STATUS_VALIDATION_JSON="$(printf '{"%s":{"_eq":"draft"}}' "$DIRECTUS_CMS_STATUS_FIELD")"
EDITOR_UPDATE_STATUS_VALIDATION_JSON="$(printf '{"%s":{"_in":["draft","in_review"]}}' "$DIRECTUS_CMS_STATUS_FIELD")"
PUBLISHER_CREATE_STATUS_VALIDATION_JSON="$(printf '{"%s":{"_eq":"draft"}}' "$DIRECTUS_CMS_STATUS_FIELD")"
PUBLISHER_UPDATE_STATUS_VALIDATION_JSON="$(printf '{"%s":{"_in":["draft","in_review","published","archived"]}}' "$DIRECTUS_CMS_STATUS_FIELD")"
CREATE_STATUS_PRESET_JSON="$(printf '{"%s":"draft"}' "$DIRECTUS_CMS_STATUS_FIELD")"
PUBLIC_PUBLISHED_FILTER_JSON="$(printf '{"%s":{"_eq":"published"}}' "$DIRECTUS_CMS_STATUS_FIELD")"

normalize_csv() {
  printf '%s' "$1" \
    | tr ',' '\n' \
    | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' \
    | awk 'NF' \
    | sort -u
}

wait_for_url() {
  local label="$1"
  local url="$2"

  for _ in $(seq 1 60); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done

  echo "Timed out waiting for $label at $url" >&2
  exit 1
}

wait_for_url "Keycloak" "http://localhost:8081/realms/cozyhome/.well-known/openid-configuration"
wait_for_url "Directus" "${DIRECTUS_PUBLIC_URL}/server/health"

DIRECTUS_MODULE_BAR_BASELINE='[
  {"type":"module","id":"content","enabled":true},
  {"type":"module","id":"visual","enabled":false},
  {"type":"module","id":"users","enabled":true},
  {"type":"module","id":"files","enabled":true},
  {"type":"module","id":"insights","enabled":true},
  {"type":"module","id":"settings","enabled":true}
]'

PUBLIC_POLICY_ID="$(
  docker exec -i directus-database-1 psql -At -U directus -d directus \
    -c "SELECT id FROM directus_policies WHERE name = '\$t:public_label' LIMIT 1;" \
  | tr -d '[:space:]'
)"

if [[ -z "$PUBLIC_POLICY_ID" ]]; then
  echo "Could not resolve Directus public policy id." >&2
  exit 1
fi

docker exec keycloak-keycloak-1 /opt/keycloak/bin/kcadm.sh config credentials \
  --server http://localhost:8080 \
  --realm master \
  --user "${KEYCLOAK_BOOTSTRAP_ADMIN_USERNAME}" \
  --password "${KEYCLOAK_BOOTSTRAP_ADMIN_PASSWORD}" >/dev/null

EXISTING_CLIENT_ID="$(
  docker exec keycloak-keycloak-1 /opt/keycloak/bin/kcadm.sh get clients -r cozyhome -q clientId="${DIRECTUS_AUTH_KEYCLOAK_CLIENT_ID}" --fields id \
  | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{const data=JSON.parse(s); console.log(data[0]?.id || "");})'
)"

if [[ -n "$EXISTING_CLIENT_ID" ]]; then
  docker exec keycloak-keycloak-1 /opt/keycloak/bin/kcadm.sh delete "clients/${EXISTING_CLIENT_ID}" -r cozyhome >/dev/null
fi

cat <<JSON >/tmp/directus-keycloak-client.json
{
  "clientId": "${DIRECTUS_AUTH_KEYCLOAK_CLIENT_ID}",
  "name": "Админка Directus",
  "description": "Локальный SSO-клиент для Directus Studio",
  "enabled": true,
  "publicClient": false,
  "secret": "${DIRECTUS_AUTH_KEYCLOAK_CLIENT_SECRET}",
  "standardFlowEnabled": true,
  "directAccessGrantsEnabled": false,
  "implicitFlowEnabled": false,
  "serviceAccountsEnabled": false,
  "frontchannelLogout": true,
  "redirectUris": [
    "http://localhost:8055/auth/login/keycloak/callback",
    "http://localhost:8055/*"
  ],
  "webOrigins": [
    "http://localhost:8055"
  ],
  "protocol": "openid-connect",
  "defaultClientScopes": [
    "web-origins",
    "acr",
    "profile",
    "roles",
    "email"
  ],
  "optionalClientScopes": [
    "address",
    "phone",
    "offline_access",
    "microprofile-jwt"
  ],
  "protocolMappers": [
    {
      "name": "realm-roles-as-groups",
      "protocol": "openid-connect",
      "protocolMapper": "oidc-usermodel-realm-role-mapper",
      "consentRequired": false,
      "config": {
        "multivalued": "true",
        "userinfo.token.claim": "true",
        "id.token.claim": "true",
        "access.token.claim": "true",
        "claim.name": "groups",
        "jsonType.label": "String"
      }
    }
  ]
}
JSON

docker cp /tmp/directus-keycloak-client.json keycloak-keycloak-1:/tmp/directus-keycloak-client.json
docker exec keycloak-keycloak-1 /opt/keycloak/bin/kcadm.sh create clients -r cozyhome -f /tmp/directus-keycloak-client.json >/dev/null
rm -f /tmp/directus-keycloak-client.json

docker exec -i directus-database-1 psql -v ON_ERROR_STOP=1 -U directus -d directus <<SQL
UPDATE directus_settings
SET default_language = '${DIRECTUS_DEFAULT_LANGUAGE}',
    project_name = '${DIRECTUS_PROJECT_NAME}';

UPDATE directus_users
SET email = '${DIRECTUS_ADMIN_EMAIL}'
WHERE provider = 'default'
  AND email = '${LEGACY_DIRECTUS_ADMIN_EMAIL}'
  AND '${DIRECTUS_ADMIN_EMAIL}' <> '${LEGACY_DIRECTUS_ADMIN_EMAIL}'
  AND NOT EXISTS (
    SELECT 1
    FROM directus_users
    WHERE email = '${DIRECTUS_ADMIN_EMAIL}'
  );

UPDATE directus_users
SET language = '${DIRECTUS_DEFAULT_LANGUAGE}'
WHERE COALESCE(language, '') = '';

DELETE FROM directus_access
WHERE id IN (
    '4c4cc8d0-9b7f-4d56-84d2-1d64f5f30001',
    '4c4cc8d0-9b7f-4d56-84d2-1d64f5f30002',
    '4c4cc8d0-9b7f-4d56-84d2-1d64f5f30003'
  )
  AND policy IN ('11111111-1111-4111-8111-111111111111');

DELETE FROM directus_policies
WHERE id = '11111111-1111-4111-8111-111111111111';

INSERT INTO directus_policies (id, name, icon, description, ip_access, enforce_tfa, admin_access, app_access)
VALUES
  ('${DIRECTUS_POLICY_CMS_ADMIN_ID}', 'Политика администратора CMS', 'verified', 'Полный доступ к администрированию Directus для администраторов Keycloak.', NULL, false, true, true),
  ('${DIRECTUS_POLICY_CMS_EDITOR_ID}', 'Политика редактора CMS', 'edit', 'Подготовка черновиков и работа с файлами без права публикации.', NULL, false, false, true),
  ('${DIRECTUS_POLICY_CMS_PUBLISHER_ID}', 'Политика публикатора CMS', 'fact_check', 'Проверка и публикация CMS-контента.', NULL, false, false, true),
  ('${DIRECTUS_POLICY_CATALOGUE_OPERATOR_ID}', 'Политика оператора каталога', 'inventory_2', 'Операции с каталогом через витринный bridge Directus.', NULL, false, false, true),
  ('${DIRECTUS_POLICY_INVENTORY_OPERATOR_ID}', 'Политика оператора остатков', 'inventory', 'Операции с вариантами, ценами и остатками через витринный bridge Directus.', NULL, false, false, true)
ON CONFLICT (id) DO UPDATE SET
  name = EXCLUDED.name,
  icon = EXCLUDED.icon,
  description = EXCLUDED.description,
  ip_access = EXCLUDED.ip_access,
  enforce_tfa = EXCLUDED.enforce_tfa,
  admin_access = EXCLUDED.admin_access,
  app_access = EXCLUDED.app_access;

INSERT INTO directus_roles (id, name, icon, description, parent)
VALUES
  ('${DIRECTUS_ROLE_CMS_ADMIN_ID}', 'Администратор CMS', 'verified_user', 'Роль, которая назначается администраторам Keycloak.', NULL),
  ('${DIRECTUS_ROLE_CMS_EDITOR_ID}', 'Редактор CMS', 'edit_note', 'Роль редактора для подготовки контента и черновиков.', NULL),
  ('${DIRECTUS_ROLE_CMS_PUBLISHER_ID}', 'Публикатор CMS', 'fact_check', 'Роль для проверки, утверждения и публикации контента.', NULL),
  ('${DIRECTUS_ROLE_CATALOGUE_OPERATOR_ID}', 'Оператор каталога', 'inventory_2', 'Роль для работы с товарами, категориями, брендами и bridge-операциями каталога.', NULL),
  ('${DIRECTUS_ROLE_INVENTORY_OPERATOR_ID}', 'Оператор остатков', 'inventory', 'Роль для работы с вариантами, ценами и остатками через bridge.', NULL)
ON CONFLICT (id) DO UPDATE SET
  name = EXCLUDED.name,
  icon = EXCLUDED.icon,
  description = EXCLUDED.description,
  parent = EXCLUDED.parent;

INSERT INTO directus_access (id, role, "user", policy, sort)
VALUES
  ('${DIRECTUS_ACCESS_CMS_ADMIN_ID}', '${DIRECTUS_ROLE_CMS_ADMIN_ID}', NULL, '${DIRECTUS_POLICY_CMS_ADMIN_ID}', NULL),
  ('${DIRECTUS_ACCESS_CMS_EDITOR_ID}', '${DIRECTUS_ROLE_CMS_EDITOR_ID}', NULL, '${DIRECTUS_POLICY_CMS_EDITOR_ID}', NULL),
  ('${DIRECTUS_ACCESS_CMS_PUBLISHER_ID}', '${DIRECTUS_ROLE_CMS_PUBLISHER_ID}', NULL, '${DIRECTUS_POLICY_CMS_PUBLISHER_ID}', NULL),
  ('${DIRECTUS_ACCESS_CATALOGUE_OPERATOR_ID}', '${DIRECTUS_ROLE_CATALOGUE_OPERATOR_ID}', NULL, '${DIRECTUS_POLICY_CATALOGUE_OPERATOR_ID}', NULL),
  ('${DIRECTUS_ACCESS_INVENTORY_OPERATOR_ID}', '${DIRECTUS_ROLE_INVENTORY_OPERATOR_ID}', NULL, '${DIRECTUS_POLICY_INVENTORY_OPERATOR_ID}', NULL)
ON CONFLICT (id) DO UPDATE SET
  role = EXCLUDED.role,
  "user" = EXCLUDED."user",
  policy = EXCLUDED.policy,
  sort = EXCLUDED.sort;

DELETE FROM directus_permissions
WHERE policy IN ('${DIRECTUS_POLICY_CMS_EDITOR_ID}', '${DIRECTUS_POLICY_CMS_PUBLISHER_ID}');

DELETE FROM directus_permissions
WHERE policy = '${PUBLIC_POLICY_ID}';

INSERT INTO directus_permissions (collection, action, permissions, validation, presets, fields, policy)
VALUES
  ('directus_files', 'read', '{}'::json, NULL, NULL, '*', '${DIRECTUS_POLICY_CMS_EDITOR_ID}'),
  ('directus_files', 'create', '{}'::json, NULL, NULL, '*', '${DIRECTUS_POLICY_CMS_EDITOR_ID}'),
  ('directus_files', 'update', '{}'::json, NULL, NULL, '*', '${DIRECTUS_POLICY_CMS_EDITOR_ID}'),
  ('directus_folders', 'read', '{}'::json, NULL, NULL, '*', '${DIRECTUS_POLICY_CMS_EDITOR_ID}'),
  ('directus_folders', 'create', '{}'::json, NULL, NULL, '*', '${DIRECTUS_POLICY_CMS_EDITOR_ID}'),
  ('directus_folders', 'update', '{}'::json, NULL, NULL, '*', '${DIRECTUS_POLICY_CMS_EDITOR_ID}'),
  ('directus_files', 'read', '{}'::json, NULL, NULL, '*', '${DIRECTUS_POLICY_CMS_PUBLISHER_ID}'),
  ('directus_files', 'create', '{}'::json, NULL, NULL, '*', '${DIRECTUS_POLICY_CMS_PUBLISHER_ID}'),
  ('directus_files', 'update', '{}'::json, NULL, NULL, '*', '${DIRECTUS_POLICY_CMS_PUBLISHER_ID}'),
  ('directus_folders', 'read', '{}'::json, NULL, NULL, '*', '${DIRECTUS_POLICY_CMS_PUBLISHER_ID}'),
  ('directus_folders', 'create', '{}'::json, NULL, NULL, '*', '${DIRECTUS_POLICY_CMS_PUBLISHER_ID}'),
  ('directus_folders', 'update', '{}'::json, NULL, NULL, '*', '${DIRECTUS_POLICY_CMS_PUBLISHER_ID}');

DELETE FROM directus_permissions
WHERE collection IN ('directus_dashboards', 'directus_panels')
  AND policy IN (
    '${DIRECTUS_POLICY_CMS_EDITOR_ID}',
    '${DIRECTUS_POLICY_CMS_PUBLISHER_ID}',
    '${DIRECTUS_POLICY_CATALOGUE_OPERATOR_ID}',
    '${DIRECTUS_POLICY_INVENTORY_OPERATOR_ID}'
  );

INSERT INTO directus_permissions (collection, action, permissions, validation, presets, fields, policy)
VALUES
  ('directus_dashboards', 'read', '{}'::json, NULL, NULL, '*', '${DIRECTUS_POLICY_CMS_EDITOR_ID}'),
  ('directus_panels', 'read', '{}'::json, NULL, NULL, '*', '${DIRECTUS_POLICY_CMS_EDITOR_ID}'),
  ('directus_dashboards', 'read', '{}'::json, NULL, NULL, '*', '${DIRECTUS_POLICY_CMS_PUBLISHER_ID}'),
  ('directus_panels', 'read', '{}'::json, NULL, NULL, '*', '${DIRECTUS_POLICY_CMS_PUBLISHER_ID}'),
  ('directus_dashboards', 'read', '{}'::json, NULL, NULL, '*', '${DIRECTUS_POLICY_CATALOGUE_OPERATOR_ID}'),
  ('directus_panels', 'read', '{}'::json, NULL, NULL, '*', '${DIRECTUS_POLICY_CATALOGUE_OPERATOR_ID}'),
  ('directus_dashboards', 'read', '{}'::json, NULL, NULL, '*', '${DIRECTUS_POLICY_INVENTORY_OPERATOR_ID}'),
  ('directus_panels', 'read', '{}'::json, NULL, NULL, '*', '${DIRECTUS_POLICY_INVENTORY_OPERATOR_ID}');

INSERT INTO directus_dashboards (id, name, icon, note, color, user_created)
VALUES
  ('${DIRECTUS_DASHBOARD_STOREFRONT_OPS_ID}', 'Оператор витрины', 'dashboard_customize', 'Резервная точка входа в рабочее место управления витриной.', 'primary', NULL)
ON CONFLICT (id) DO UPDATE SET
  name = EXCLUDED.name,
  icon = EXCLUDED.icon,
  note = EXCLUDED.note,
  color = EXCLUDED.color,
  user_created = EXCLUDED.user_created;

INSERT INTO directus_panels (
  id,
  dashboard,
  name,
  icon,
  color,
  show_header,
  note,
  type,
  position_x,
  position_y,
  width,
  height,
  options,
  user_created
)
VALUES (
  '${DIRECTUS_PANEL_STOREFRONT_OPS_LAUNCHER_ID}',
  '${DIRECTUS_DASHBOARD_STOREFRONT_OPS_ID}',
  'Запуск рабочего места',
  'dashboard_customize',
  'primary',
  true,
  'Быстрый вход в разделы товаров, категорий, брендов и остатков.',
  'storefront-ops-launcher',
  1,
  1,
  12,
  10,
  '{}'::json,
  NULL
)
ON CONFLICT (id) DO UPDATE SET
  dashboard = EXCLUDED.dashboard,
  name = EXCLUDED.name,
  icon = EXCLUDED.icon,
  color = EXCLUDED.color,
  show_header = EXCLUDED.show_header,
  note = EXCLUDED.note,
  type = EXCLUDED.type,
  position_x = EXCLUDED.position_x,
  position_y = EXCLUDED.position_y,
  width = EXCLUDED.width,
  height = EXCLUDED.height,
  options = EXCLUDED.options,
  user_created = EXCLUDED.user_created;
SQL

CURRENT_DIRECTUS_MODULE_BAR="$(
  docker exec -i directus-database-1 psql -At -U directus -d directus \
    -c "SELECT COALESCE(module_bar::text, 'null') FROM directus_settings LIMIT 1;" \
  | tr -d '\r'
)"

NEXT_DIRECTUS_MODULE_BAR="$(
  printf '%s' "${CURRENT_DIRECTUS_MODULE_BAR:-null}" | jq -c --argjson baseline "$DIRECTUS_MODULE_BAR_BASELINE" '
    def storefront: {"type":"module","id":"storefront-ops","enabled":true};
    (if type == "array" then . else $baseline end)
    | map(if .id == "storefront-ops" then (. + {"type":"module","enabled":true}) else . end)
    | map(select(.id != "storefront-ops"))
    | if any(.[]; .id == "content")
      then (map(if .id == "content" then [., storefront] else [.] end) | add)
      else [storefront] + .
      end
  '
)"

docker exec -i directus-database-1 psql -v ON_ERROR_STOP=1 -U directus -d directus <<SQL
UPDATE directus_settings
SET module_bar = '${NEXT_DIRECTUS_MODULE_BAR}'::json;
SQL

while IFS= read -r collection; do
  docker exec -i directus-database-1 psql -v ON_ERROR_STOP=1 -U directus -d directus <<SQL
\set collection '${collection}'
\set editor_policy '${DIRECTUS_POLICY_CMS_EDITOR_ID}'
\set publisher_policy '${DIRECTUS_POLICY_CMS_PUBLISHER_ID}'
\set editor_create_status_validation '${EDITOR_CREATE_STATUS_VALIDATION_JSON}'
\set editor_update_status_validation '${EDITOR_UPDATE_STATUS_VALIDATION_JSON}'
\set publisher_create_status_validation '${PUBLISHER_CREATE_STATUS_VALIDATION_JSON}'
\set publisher_update_status_validation '${PUBLISHER_UPDATE_STATUS_VALIDATION_JSON}'
\set create_status_preset '${CREATE_STATUS_PRESET_JSON}'

DELETE FROM directus_permissions
WHERE collection = :'collection'
  AND policy IN (:'editor_policy', :'publisher_policy');

INSERT INTO directus_permissions (collection, action, permissions, validation, presets, fields, policy)
VALUES
  (:'collection', 'read', '{}'::json, NULL, NULL, '*', :'editor_policy'),
  (:'collection', 'create', '{}'::json, :'editor_create_status_validation'::json, :'create_status_preset'::json, '*', :'editor_policy'),
  (:'collection', 'update', '{}'::json, :'editor_update_status_validation'::json, NULL, '*', :'editor_policy'),
  (:'collection', 'read', '{}'::json, NULL, NULL, '*', :'publisher_policy'),
  (:'collection', 'create', '{}'::json, :'publisher_create_status_validation'::json, :'create_status_preset'::json, '*', :'publisher_policy'),
  (:'collection', 'update', '{}'::json, :'publisher_update_status_validation'::json, NULL, '*', :'publisher_policy');
SQL
done < <(normalize_csv "$DIRECTUS_CMS_CONTENT_COLLECTIONS")

while IFS= read -r collection; do
  docker exec -i directus-database-1 psql -v ON_ERROR_STOP=1 -U directus -d directus <<SQL
\set collection '${collection}'
\set public_policy '${PUBLIC_POLICY_ID}'
\set public_published_filter '${PUBLIC_PUBLISHED_FILTER_JSON}'

DELETE FROM directus_permissions
WHERE collection = :'collection'
  AND policy = :'public_policy';

INSERT INTO directus_permissions (collection, action, permissions, validation, presets, fields, policy)
VALUES
  (:'collection', 'read', :'public_published_filter'::json, NULL, NULL, '*', :'public_policy');
SQL
done < <(normalize_csv "$DIRECTUS_CMS_PUBLIC_COLLECTIONS")

echo "Directus Keycloak SSO and governance bootstrap completed."
