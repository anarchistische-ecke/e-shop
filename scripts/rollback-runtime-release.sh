#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"
COMPOSE_FILE="$ROOT_DIR/docker-compose.prod.yml"
TARGET_RELEASE_ID=""
ROLLBACK_RUN_ID="${ROLLBACK_RUN_ID:-manual}"
WAIT_TIMEOUT_SECONDS="${WAIT_TIMEOUT_SECONDS:-180}"

# shellcheck source=scripts/lib/env-file.sh
source "$ROOT_DIR/scripts/lib/env-file.sh"
# shellcheck source=scripts/lib/runtime-release.sh
source "$ROOT_DIR/scripts/lib/runtime-release.sh"

usage() {
  cat <<'EOF'
Usage:
  ./scripts/rollback-runtime-release.sh [--env-file <path>] [--compose-file <path>] [--release-id <id>] [--run-id <id>]
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file)
      ENV_FILE="$(resolve_env_file_path "$2")"
      shift 2
      ;;
    --compose-file)
      COMPOSE_FILE="$(resolve_env_file_path "$2")"
      shift 2
      ;;
    --release-id)
      TARGET_RELEASE_ID="$2"
      shift 2
      ;;
    --run-id)
      ROLLBACK_RUN_ID="$2"
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

load_env_file "$ENV_FILE"
runtime_set_defaults
runtime_prepare_dirs
runtime_load_state

require_env PUBLIC_API_HEALTHCHECK_URL "$ENV_FILE"
require_env PUBLIC_CONTENT_HEALTHCHECK_URL "$ENV_FILE"
require_env DIRECTUS_PUBLIC_URL "$ENV_FILE"

exec 9>"$(runtime_lock_file)"
if ! flock -n 9; then
  echo "Another runtime deploy or rollback is already in progress." >&2
  exit 1
fi

if [[ -z "${PREVIOUS_LIVE_RELEASE_ID:-}" ]]; then
  echo "No previous live release is recorded; runtime rollback cannot proceed." >&2
  exit 1
fi

if [[ -z "$TARGET_RELEASE_ID" ]]; then
  TARGET_RELEASE_ID="$PREVIOUS_LIVE_RELEASE_ID"
fi

if [[ "$TARGET_RELEASE_ID" != "$PREVIOUS_LIVE_RELEASE_ID" ]]; then
  echo "Only rollback to the recorded previous live release is supported. Requested ${TARGET_RELEASE_ID}, recorded ${PREVIOUS_LIVE_RELEASE_ID}." >&2
  exit 1
fi

TARGET_RELEASE_DIR="$PREVIOUS_LIVE_RELEASE_DIR"
TARGET_SLOT="$PREVIOUS_LIVE_SLOT"
TARGET_PROJECT="$PREVIOUS_LIVE_PROJECT"
TARGET_API_PORT="$PREVIOUS_LIVE_API_PORT"
TARGET_DIRECTUS_PORT="$PREVIOUS_LIVE_DIRECTUS_PORT"
TARGET_STOREFRONT_PORT="$PREVIOUS_LIVE_STOREFRONT_PORT"
CURRENT_PROJECT_BEFORE_ROLLBACK="${CURRENT_LIVE_PROJECT:-}"
CURRENT_RELEASE_ID_BEFORE_ROLLBACK="${CURRENT_LIVE_RELEASE_ID:-}"
CURRENT_RELEASE_DIR_BEFORE_ROLLBACK="${CURRENT_LIVE_RELEASE_DIR:-}"
CURRENT_SLOT_BEFORE_ROLLBACK="${CURRENT_LIVE_SLOT:-}"
CURRENT_API_PORT_BEFORE_ROLLBACK="${CURRENT_LIVE_API_PORT:-}"
CURRENT_DIRECTUS_PORT_BEFORE_ROLLBACK="${CURRENT_LIVE_DIRECTUS_PORT:-}"
CURRENT_STOREFRONT_PORT_BEFORE_ROLLBACK="${CURRENT_LIVE_STOREFRONT_PORT:-}"
ROLLBACK_LOG_FILE="$(runtime_log_file_for "runtime-rollback" "$TARGET_RELEASE_ID")"
CURRENT_PHASE="initializing"
CUTOVER_APPLIED=false
API_INCLUDE_BACKUP=""
CMS_INCLUDE_BACKUP=""
STOREFRONT_INCLUDE_BACKUP=""

if [[ -z "$TARGET_RELEASE_DIR" || -z "$TARGET_SLOT" || -z "$TARGET_PROJECT" || -z "$TARGET_API_PORT" || -z "$TARGET_DIRECTUS_PORT" || -z "$TARGET_STOREFRONT_PORT" ]]; then
  echo "Recorded previous live release metadata is incomplete." >&2
  exit 1
fi

if [[ ! -d "$TARGET_RELEASE_DIR" ]]; then
  echo "Recorded previous live release directory is missing: $TARGET_RELEASE_DIR" >&2
  exit 1
fi

exec > >(tee -a "$ROLLBACK_LOG_FILE") 2>&1

candidate_compose() {
  local compose_file

  compose_file="$(runtime_resolved_compose_file "$TARGET_RELEASE_DIR")"

  if docker compose version >/dev/null 2>&1; then
    docker compose \
      --project-name "$TARGET_PROJECT" \
      --env-file "$ENV_FILE" \
      -f "$compose_file" \
      "$@"
  elif command -v docker-compose >/dev/null 2>&1; then
    docker-compose \
      --project-name "$TARGET_PROJECT" \
      --env-file "$ENV_FILE" \
      -f "$compose_file" \
      "$@"
  else
    echo "Docker Compose not found." >&2
    exit 1
  fi
}

restore_previous_upstreams() {
  if [[ -n "$API_INCLUDE_BACKUP" && -f "$API_INCLUDE_BACKUP" ]]; then
    cp "$API_INCLUDE_BACKUP" "$DEPLOY_NGINX_API_UPSTREAM_INCLUDE"
  fi

  if [[ -n "$CMS_INCLUDE_BACKUP" && -f "$CMS_INCLUDE_BACKUP" ]]; then
    cp "$CMS_INCLUDE_BACKUP" "$DEPLOY_NGINX_CMS_UPSTREAM_INCLUDE"
  fi

  if [[ -n "$STOREFRONT_INCLUDE_BACKUP" && -f "$STOREFRONT_INCLUDE_BACKUP" ]]; then
    cp "$STOREFRONT_INCLUDE_BACKUP" "$DEPLOY_NGINX_STOREFRONT_UPSTREAM_INCLUDE"
  fi

  runtime_reload_nginx
}

on_error() {
  local exit_code="$1"
  local line_number="$2"

  echo "Runtime rollback failed during phase '$CURRENT_PHASE' at line ${line_number}." >&2
  candidate_compose ps || true
  candidate_compose logs --tail=200 api directus storefront || true

  if [[ "$CUTOVER_APPLIED" == "true" ]]; then
    restore_previous_upstreams || true
  fi

  cat >"$(runtime_summary_file)" <<EOF
status=failure
phase=$CURRENT_PHASE
rollback_release_id=$TARGET_RELEASE_ID
run_id=$ROLLBACK_RUN_ID
log_file=$ROLLBACK_LOG_FILE
EOF

  exit "$exit_code"
}

trap 'on_error $? $LINENO' ERR

if runtime_compose_has_service "$(runtime_resolved_compose_file "$TARGET_RELEASE_DIR")" storefront; then
  require_env STOREFRONT_IMAGE_REPOSITORY "$ENV_FILE"
  require_env STOREFRONT_IMAGE_TAG "$ENV_FILE"
fi

export API_IMAGE_REPOSITORY="${API_IMAGE_REPOSITORY:-ghcr.io/anarchistische-ecke/eshop-api}"
export API_IMAGE_TAG="${TARGET_RELEASE_ID}"
export STOREFRONT_IMAGE_REPOSITORY="${STOREFRONT_IMAGE_REPOSITORY:-}"
export STOREFRONT_IMAGE_TAG="${STOREFRONT_IMAGE_TAG:-}"
export RUNTIME_ENV_FILE="$ENV_FILE"
export RUNTIME_RELEASE_DIR="$TARGET_RELEASE_DIR"
export RUNTIME_RELEASE_ID="$TARGET_RELEASE_ID"
export RUNTIME_SLOT="$TARGET_SLOT"
export RUNTIME_API_HOST_PORT="$TARGET_API_PORT"
export RUNTIME_DIRECTUS_HOST_PORT="$TARGET_DIRECTUS_PORT"
export RUNTIME_STOREFRONT_HOST_PORT="$TARGET_STOREFRONT_PORT"
export DEPLOY_SHARED_DOCKER_NETWORK

CURRENT_PHASE="start-target-runtime"
candidate_compose up -d

CURRENT_PHASE="internal-health"
bash "$ROOT_DIR/scripts/check-stack-health.sh" \
  --env-file "$ENV_FILE" \
  --compose-file "$COMPOSE_FILE" \
  --api-url "$(runtime_internal_api_url "$TARGET_API_PORT")" \
  --directus-url "$(runtime_internal_directus_url "$TARGET_DIRECTUS_PORT")" \
  --storefront-url "$(runtime_internal_storefront_url "$TARGET_STOREFRONT_PORT")" \
  --content-url "$(runtime_internal_content_url "$TARGET_API_PORT")" \
  --skip-public \
  --timeout-seconds "$WAIT_TIMEOUT_SECONDS"

CURRENT_PHASE="cutover"
API_INCLUDE_BACKUP="$(mktemp "${DEPLOY_RUNTIME_STATE_DIR}/rollback-api.XXXXXX.bak")"
CMS_INCLUDE_BACKUP="$(mktemp "${DEPLOY_RUNTIME_STATE_DIR}/rollback-cms.XXXXXX.bak")"
STOREFRONT_INCLUDE_BACKUP="$(mktemp "${DEPLOY_RUNTIME_STATE_DIR}/rollback-storefront.XXXXXX.bak")"
cp "$DEPLOY_NGINX_API_UPSTREAM_INCLUDE" "$API_INCLUDE_BACKUP"
cp "$DEPLOY_NGINX_CMS_UPSTREAM_INCLUDE" "$CMS_INCLUDE_BACKUP"
cp "$DEPLOY_NGINX_STOREFRONT_UPSTREAM_INCLUDE" "$STOREFRONT_INCLUDE_BACKUP"
runtime_write_upstream_include "$DEPLOY_NGINX_API_UPSTREAM_INCLUDE" "$TARGET_API_PORT"
runtime_write_upstream_include "$DEPLOY_NGINX_CMS_UPSTREAM_INCLUDE" "$TARGET_DIRECTUS_PORT"
runtime_write_upstream_include "$DEPLOY_NGINX_STOREFRONT_UPSTREAM_INCLUDE" "$TARGET_STOREFRONT_PORT"
runtime_reload_nginx
CUTOVER_APPLIED=true

CURRENT_PHASE="public-health"
bash "$ROOT_DIR/scripts/check-stack-health.sh" \
  --env-file "$ENV_FILE" \
  --compose-file "$COMPOSE_FILE" \
  --api-url "$(runtime_internal_api_url "$TARGET_API_PORT")" \
  --directus-url "$(runtime_internal_directus_url "$TARGET_DIRECTUS_PORT")" \
  --storefront-url "$(runtime_internal_storefront_url "$TARGET_STOREFRONT_PORT")" \
  --content-url "$(runtime_internal_content_url "$TARGET_API_PORT")" \
  --public-api-url "$PUBLIC_API_HEALTHCHECK_URL" \
  --public-directus-url "$(runtime_public_directus_url)" \
  --public-storefront-url "$(runtime_public_storefront_url)" \
  --public-content-url "$PUBLIC_CONTENT_HEALTHCHECK_URL" \
  --timeout-seconds "$WAIT_TIMEOUT_SECONDS"

CURRENT_LIVE_RELEASE_ID="$TARGET_RELEASE_ID"
CURRENT_LIVE_RELEASE_DIR="$TARGET_RELEASE_DIR"
CURRENT_LIVE_SLOT="$TARGET_SLOT"
CURRENT_LIVE_PROJECT="$TARGET_PROJECT"
CURRENT_LIVE_API_PORT="$TARGET_API_PORT"
CURRENT_LIVE_DIRECTUS_PORT="$TARGET_DIRECTUS_PORT"
CURRENT_LIVE_STOREFRONT_PORT="$TARGET_STOREFRONT_PORT"
PREVIOUS_LIVE_RELEASE_ID="$CURRENT_RELEASE_ID_BEFORE_ROLLBACK"
PREVIOUS_LIVE_RELEASE_DIR="$CURRENT_RELEASE_DIR_BEFORE_ROLLBACK"
PREVIOUS_LIVE_SLOT="$CURRENT_SLOT_BEFORE_ROLLBACK"
PREVIOUS_LIVE_PROJECT="$CURRENT_PROJECT_BEFORE_ROLLBACK"
PREVIOUS_LIVE_API_PORT="$CURRENT_API_PORT_BEFORE_ROLLBACK"
PREVIOUS_LIVE_DIRECTUS_PORT="$CURRENT_DIRECTUS_PORT_BEFORE_ROLLBACK"
PREVIOUS_LIVE_STOREFRONT_PORT="$CURRENT_STOREFRONT_PORT_BEFORE_ROLLBACK"

LAST_DEPLOYED_SHA="$TARGET_RELEASE_ID"
LAST_DEPLOYED_REF="rollback"
LAST_DEPLOY_STATUS="rolled-back"
LAST_DEPLOY_RUN_ID="$ROLLBACK_RUN_ID"
LAST_DEPLOY_AT="$(runtime_current_timestamp)"
runtime_write_state

CURRENT_PHASE="retire-current-runtime"
if [[ -n "$CURRENT_PROJECT_BEFORE_ROLLBACK" && "$CURRENT_PROJECT_BEFORE_ROLLBACK" != "$TARGET_PROJECT" && -n "$CURRENT_RELEASE_DIR_BEFORE_ROLLBACK" ]]; then
  export RUNTIME_RELEASE_DIR="$CURRENT_RELEASE_DIR_BEFORE_ROLLBACK"
  export RUNTIME_RELEASE_ID="${CURRENT_RELEASE_ID_BEFORE_ROLLBACK:-$TARGET_RELEASE_ID}"
  export RUNTIME_SLOT="$CURRENT_SLOT_BEFORE_ROLLBACK"
  export RUNTIME_API_HOST_PORT="$CURRENT_API_PORT_BEFORE_ROLLBACK"
  export RUNTIME_DIRECTUS_HOST_PORT="$CURRENT_DIRECTUS_PORT_BEFORE_ROLLBACK"
  export RUNTIME_STOREFRONT_HOST_PORT="$CURRENT_STOREFRONT_PORT_BEFORE_ROLLBACK"
  TARGET_RELEASE_DIR="$CURRENT_RELEASE_DIR_BEFORE_ROLLBACK"
  TARGET_PROJECT="$CURRENT_PROJECT_BEFORE_ROLLBACK"
  candidate_compose down --remove-orphans || true
fi

CURRENT_PHASE="complete"
cat >"$(runtime_summary_file)" <<EOF
status=success
phase=$CURRENT_PHASE
rollback_release_id=$TARGET_RELEASE_ID
run_id=$ROLLBACK_RUN_ID
storefront_port=$CURRENT_LIVE_STOREFRONT_PORT
log_file=$ROLLBACK_LOG_FILE
EOF

echo "Runtime rollback completed successfully."
