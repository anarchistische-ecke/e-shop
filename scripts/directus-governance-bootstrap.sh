#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"
COMPOSE_FILE="$ROOT_DIR/docker-compose.prod.yml"
DATABASE_SERVICE="postgres"

usage() {
  cat <<'EOF'
Usage:
  ./scripts/directus-governance-bootstrap.sh [--env-file <path>] [--compose-file <path>] [--database-service <name>]
EOF
}

resolve_path() {
  case "$1" in
    /*) printf '%s\n' "$1" ;;
    *) printf '%s\n' "$PWD/$1" ;;
  esac
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file)
      ENV_FILE="$(resolve_path "$2")"
      shift 2
      ;;
    --compose-file)
      COMPOSE_FILE="$(resolve_path "$2")"
      shift 2
      ;;
    --database-service)
      DATABASE_SERVICE="$2"
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

if [[ ! -f "$COMPOSE_FILE" ]]; then
  echo "Missing compose file: $COMPOSE_FILE" >&2
  exit 1
fi

set -a
source "$ENV_FILE"
set +a

: "${POSTGRES_USER:?Set POSTGRES_USER in $ENV_FILE}"
: "${POSTGRES_PASSWORD:?Set POSTGRES_PASSWORD in $ENV_FILE}"
: "${DIRECTUS_DB_DATABASE:?Set DIRECTUS_DB_DATABASE in $ENV_FILE}"

DIRECTUS_DEFAULT_LANGUAGE="${DIRECTUS_DEFAULT_LANGUAGE:-ru-RU}"
DIRECTUS_PROJECT_NAME="${DIRECTUS_PROJECT_NAME:-Магазин}"
LEGACY_DIRECTUS_ADMIN_EMAIL="${LEGACY_DIRECTUS_ADMIN_EMAIL:-admin@example.com}"
DIRECTUS_ADMIN_EMAIL="${DIRECTUS_ADMIN_EMAIL:-$LEGACY_DIRECTUS_ADMIN_EMAIL}"
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
DIRECTUS_MODULE_BAR='[
  {"type":"module","id":"content","enabled":true},
  {"type":"module","id":"storefront-ops","enabled":true},
  {"type":"module","id":"visual","enabled":false},
  {"type":"module","id":"users","enabled":true},
  {"type":"module","id":"files","enabled":true},
  {"type":"module","id":"insights","enabled":true},
  {"type":"module","id":"settings","enabled":true}
]'

normalize_csv() {
  printf '%s' "$1" \
    | tr ',' '\n' \
    | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' \
    | awk 'NF' \
    | sort -u
}

compose() {
  if docker compose version >/dev/null 2>&1; then
    docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
  elif command -v docker-compose >/dev/null 2>&1; then
    docker-compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
  else
    echo "Docker Compose not found." >&2
    exit 1
  fi
}

run_psql_directus() {
  local sql="$1"
  compose exec -T \
    -e PGPASSWORD="$POSTGRES_PASSWORD" \
    "$DATABASE_SERVICE" \
    psql \
      --username "$POSTGRES_USER" \
      --dbname "$DIRECTUS_DB_DATABASE" \
      -v ON_ERROR_STOP=1 <<SQL
$sql
SQL
}

compose up -d "$DATABASE_SERVICE" >/dev/null

PUBLIC_POLICY_ID="$(
  compose exec -T \
    -e PGPASSWORD="$POSTGRES_PASSWORD" \
    "$DATABASE_SERVICE" \
    psql \
      --username "$POSTGRES_USER" \
      --dbname "$DIRECTUS_DB_DATABASE" \
      --tuples-only \
      --no-align \
      -c "SELECT id FROM directus_policies WHERE name = '\$t:public_label' LIMIT 1;" | tr -d '[:space:]\r'
)"

if [[ -z "$PUBLIC_POLICY_ID" ]]; then
  echo "Could not resolve Directus public policy id." >&2
  exit 1
fi

run_psql_directus "
UPDATE directus_settings
SET default_language = '${DIRECTUS_DEFAULT_LANGUAGE}',
    project_name = '${DIRECTUS_PROJECT_NAME}',
    module_bar = '${DIRECTUS_MODULE_BAR}'::json;

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
  ('${DIRECTUS_POLICY_INVENTORY_OPERATOR_ID}', 'Политика оператора остатков', 'inventory', 'Операции с вариантами, ценами и остатками через bridge.', NULL, false, false, true)
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

INSERT INTO directus_access (id, role, \"user\", policy, sort)
VALUES
  ('${DIRECTUS_ACCESS_CMS_ADMIN_ID}', '${DIRECTUS_ROLE_CMS_ADMIN_ID}', NULL, '${DIRECTUS_POLICY_CMS_ADMIN_ID}', NULL),
  ('${DIRECTUS_ACCESS_CMS_EDITOR_ID}', '${DIRECTUS_ROLE_CMS_EDITOR_ID}', NULL, '${DIRECTUS_POLICY_CMS_EDITOR_ID}', NULL),
  ('${DIRECTUS_ACCESS_CMS_PUBLISHER_ID}', '${DIRECTUS_ROLE_CMS_PUBLISHER_ID}', NULL, '${DIRECTUS_POLICY_CMS_PUBLISHER_ID}', NULL),
  ('${DIRECTUS_ACCESS_CATALOGUE_OPERATOR_ID}', '${DIRECTUS_ROLE_CATALOGUE_OPERATOR_ID}', NULL, '${DIRECTUS_POLICY_CATALOGUE_OPERATOR_ID}', NULL),
  ('${DIRECTUS_ACCESS_INVENTORY_OPERATOR_ID}', '${DIRECTUS_ROLE_INVENTORY_OPERATOR_ID}', NULL, '${DIRECTUS_POLICY_INVENTORY_OPERATOR_ID}', NULL)
ON CONFLICT (id) DO UPDATE SET
  role = EXCLUDED.role,
  \"user\" = EXCLUDED.\"user\",
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
"

while IFS= read -r collection; do
  run_psql_directus "
\\set collection '${collection}'
\\set editor_policy '${DIRECTUS_POLICY_CMS_EDITOR_ID}'
\\set publisher_policy '${DIRECTUS_POLICY_CMS_PUBLISHER_ID}'
\\set editor_create_status_validation '${EDITOR_CREATE_STATUS_VALIDATION_JSON}'
\\set editor_update_status_validation '${EDITOR_UPDATE_STATUS_VALIDATION_JSON}'
\\set publisher_create_status_validation '${PUBLISHER_CREATE_STATUS_VALIDATION_JSON}'
\\set publisher_update_status_validation '${PUBLISHER_UPDATE_STATUS_VALIDATION_JSON}'
\\set create_status_preset '${CREATE_STATUS_PRESET_JSON}'

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
"
done < <(normalize_csv "$DIRECTUS_CMS_CONTENT_COLLECTIONS")

while IFS= read -r collection; do
  run_psql_directus "
\\set collection '${collection}'
\\set public_policy '${PUBLIC_POLICY_ID}'
\\set public_published_filter '${PUBLIC_PUBLISHED_FILTER_JSON}'

DELETE FROM directus_permissions
WHERE collection = :'collection'
  AND policy = :'public_policy';

INSERT INTO directus_permissions (collection, action, permissions, validation, presets, fields, policy)
VALUES
  (:'collection', 'read', :'public_published_filter'::json, NULL, NULL, '*', :'public_policy');
"
done < <(normalize_csv "$DIRECTUS_CMS_PUBLIC_COLLECTIONS")

echo "Directus governance bootstrap completed."
