#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"
COMPOSE_FILE="$ROOT_DIR/docker-compose.runtime-slot.yml"
STOREFRONT_IMAGE_REPOSITORY="${STOREFRONT_IMAGE_REPOSITORY:-}"
STOREFRONT_IMAGE_TAG="${STOREFRONT_IMAGE_TAG:-}"
DEPLOY_RUN_ID="${DEPLOY_RUN_ID:-manual}"
WAIT_TIMEOUT_SECONDS="${WAIT_TIMEOUT_SECONDS:-120}"

# shellcheck source=scripts/lib/env-file.sh
source "$ROOT_DIR/scripts/lib/env-file.sh"
# shellcheck source=scripts/lib/runtime-release.sh
source "$ROOT_DIR/scripts/lib/runtime-release.sh"

usage() {
  cat <<'EOF'
Usage:
  ./scripts/deploy-storefront-image.sh \
    --env-file <path> \
    --compose-file <path> \
    --storefront-image-repository <image> \
    --storefront-image-tag <tag> \
    --run-id <id> \
    [--timeout-seconds <seconds>]
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
    --storefront-image-repository)
      STOREFRONT_IMAGE_REPOSITORY="$2"
      shift 2
      ;;
    --storefront-image-tag)
      STOREFRONT_IMAGE_TAG="$2"
      shift 2
      ;;
    --run-id)
      DEPLOY_RUN_ID="$2"
      shift 2
      ;;
    --timeout-seconds)
      WAIT_TIMEOUT_SECONDS="$2"
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

CLI_STOREFRONT_IMAGE_REPOSITORY="$STOREFRONT_IMAGE_REPOSITORY"
CLI_STOREFRONT_IMAGE_TAG="$STOREFRONT_IMAGE_TAG"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing env file: $ENV_FILE" >&2
  exit 1
fi

if [[ ! -f "$COMPOSE_FILE" ]]; then
  echo "Missing compose file: $COMPOSE_FILE" >&2
  exit 1
fi

load_env_file "$ENV_FILE"

STOREFRONT_IMAGE_REPOSITORY="${CLI_STOREFRONT_IMAGE_REPOSITORY:-${STOREFRONT_IMAGE_REPOSITORY:-}}"
STOREFRONT_IMAGE_TAG="${CLI_STOREFRONT_IMAGE_TAG:-${STOREFRONT_IMAGE_TAG:-}}"

runtime_set_defaults
export RUNTIME_ROOT_COMPOSE_FILE="$COMPOSE_FILE"
runtime_prepare_dirs

require_env STOREFRONT_IMAGE_REPOSITORY "storefront deploy arguments"
require_env STOREFRONT_IMAGE_TAG "storefront deploy arguments"

exec 9>"$(runtime_lock_file)"
if ! flock -n 9; then
  echo "Another runtime deploy or rollback is already in progress." >&2
  exit 1
fi

CURRENT_PHASE="preflight"
LIVE_COMPOSE_FILE=""
LOG_FILE=""

storefront_summary_file() {
  printf '%s/latest-storefront-summary.txt\n' "$DEPLOY_RUNTIME_STATE_DIR"
}

safe_log_identifier() {
  printf '%s' "$1" | tr -c 'A-Za-z0-9_.-' '_'
}

live_compose() {
  if docker compose version >/dev/null 2>&1; then
    docker compose \
      --project-name "$CURRENT_LIVE_PROJECT" \
      --env-file "$ENV_FILE" \
      -f "$LIVE_COMPOSE_FILE" \
      "$@"
  elif command -v docker-compose >/dev/null 2>&1; then
    docker-compose \
      --project-name "$CURRENT_LIVE_PROJECT" \
      --env-file "$ENV_FILE" \
      -f "$LIVE_COMPOSE_FILE" \
      "$@"
  else
    echo "Docker Compose not found." >&2
    exit 1
  fi
}

write_summary() {
  local status="$1"
  local timestamp summary_file

  timestamp="$(runtime_current_timestamp)"
  summary_file="$(storefront_summary_file)"
  mkdir -p "$(dirname "$summary_file")"
  cat >"$summary_file" <<EOF
status=$status
phase=$CURRENT_PHASE
run_id=$DEPLOY_RUN_ID
timestamp=$timestamp
project=${CURRENT_LIVE_PROJECT:-}
slot=${CURRENT_LIVE_SLOT:-}
release_id=${CURRENT_LIVE_RELEASE_ID:-}
release_dir=${CURRENT_LIVE_RELEASE_DIR:-}
storefront_port=${CURRENT_LIVE_STOREFRONT_PORT:-}
storefront_image_repository=${STOREFRONT_IMAGE_REPOSITORY:-}
storefront_image_tag=${STOREFRONT_IMAGE_TAG:-}
compose_file=${LIVE_COMPOSE_FILE:-}
log_file=${LOG_FILE:-}
EOF
}

on_error() {
  local exit_code="$1"
  local line_number="$2"

  echo "Storefront deploy failed during phase '$CURRENT_PHASE' at line ${line_number}." >&2
  if [[ -n "${LIVE_COMPOSE_FILE:-}" && -n "${CURRENT_LIVE_PROJECT:-}" ]]; then
    live_compose ps || true
    live_compose logs --tail=200 storefront || true
  fi
  write_summary failure || true
  exit "$exit_code"
}

trap 'on_error $? $LINENO' ERR

resolve_running_service_image() {
  local service_name="$1"

  docker ps \
    --filter "label=com.docker.compose.project=${CURRENT_LIVE_PROJECT}" \
    --filter "label=com.docker.compose.service=${service_name}" \
    --format '{{.Image}}' \
    | head -n 1
}

ensure_current_api_image_env() {
  local api_image last_segment

  if [[ -n "${API_IMAGE_REPOSITORY:-}" && -n "${API_IMAGE_TAG:-}" ]]; then
    export API_IMAGE_REPOSITORY API_IMAGE_TAG
    return 0
  fi

  api_image="$(resolve_running_service_image api)"
  if [[ -z "$api_image" ]]; then
    echo "Cannot infer API image because the live api container is not running." >&2
    exit 1
  fi

  last_segment="${api_image##*/}"
  if [[ "$last_segment" != *:* ]]; then
    echo "Cannot split running API image into repository and tag: $api_image" >&2
    exit 1
  fi

  API_IMAGE_REPOSITORY="${api_image%:*}"
  API_IMAGE_TAG="${api_image##*:}"
  export API_IMAGE_REPOSITORY API_IMAGE_TAG
}

export_live_runtime_env() {
  export STOREFRONT_IMAGE_REPOSITORY
  export STOREFRONT_IMAGE_TAG
  export RUNTIME_ENV_FILE="$ENV_FILE"
  export RUNTIME_RELEASE_DIR="$CURRENT_LIVE_RELEASE_DIR"
  export RUNTIME_RELEASE_ID="$CURRENT_LIVE_RELEASE_ID"
  export RUNTIME_SLOT="$CURRENT_LIVE_SLOT"
  export RUNTIME_API_HOST_PORT="$CURRENT_LIVE_API_PORT"
  export RUNTIME_DIRECTUS_HOST_PORT="$CURRENT_LIVE_DIRECTUS_PORT"
  export RUNTIME_STOREFRONT_HOST_PORT="$CURRENT_LIVE_STOREFRONT_PORT"
  export DEPLOY_SHARED_DOCKER_NETWORK
}

check_url() {
  local label="$1"
  local url="$2"
  local deadline
  shift 2

  deadline=$((SECONDS + WAIT_TIMEOUT_SECONDS))
  while true; do
    if curl --silent --show-error --fail --max-time 10 "$@" "$url" >/dev/null; then
      echo "[ok] $label: $url"
      return 0
    fi

    if (( SECONDS >= deadline )); then
      echo "[fail] $label did not become healthy before timeout: $url" >&2
      return 1
    fi

    sleep 3
  done
}

runtime_load_state
require_env CURRENT_LIVE_PROJECT "$(runtime_state_file)"
require_env CURRENT_LIVE_SLOT "$(runtime_state_file)"
require_env CURRENT_LIVE_RELEASE_ID "$(runtime_state_file)"
require_env CURRENT_LIVE_RELEASE_DIR "$(runtime_state_file)"
require_env CURRENT_LIVE_API_PORT "$(runtime_state_file)"
require_env CURRENT_LIVE_DIRECTUS_PORT "$(runtime_state_file)"
require_env CURRENT_LIVE_STOREFRONT_PORT "$(runtime_state_file)"

LOG_FILE="$(runtime_log_file_for "storefront-deploy" "$(safe_log_identifier "$STOREFRONT_IMAGE_TAG")")"
exec > >(tee -a "$LOG_FILE") 2>&1

CURRENT_PHASE="resolve-live-compose"
LIVE_COMPOSE_FILE="$(runtime_resolved_compose_file "$CURRENT_LIVE_RELEASE_DIR")"
if ! runtime_compose_has_service "$LIVE_COMPOSE_FILE" storefront; then
  echo "Resolved compose file does not define storefront: $LIVE_COMPOSE_FILE" >&2
  exit 1
fi

ensure_current_api_image_env
export_live_runtime_env

echo "Starting storefront-only deploy."
echo "Run id: $DEPLOY_RUN_ID"
echo "Live project: $CURRENT_LIVE_PROJECT"
echo "Live slot: $CURRENT_LIVE_SLOT"
echo "Live storefront port: $CURRENT_LIVE_STOREFRONT_PORT"
echo "Compose file: $LIVE_COMPOSE_FILE"
echo "Storefront image: ${STOREFRONT_IMAGE_REPOSITORY}:${STOREFRONT_IMAGE_TAG}"

CURRENT_PHASE="pull-storefront-image"
live_compose pull storefront

CURRENT_PHASE="restart-live-storefront"
live_compose up -d --no-deps storefront

CURRENT_PHASE="verify-live-storefront"
check_url "Storefront health" "$(runtime_internal_storefront_url "$CURRENT_LIVE_STOREFRONT_PORT")"
check_url "Storefront root" "http://127.0.0.1:${CURRENT_LIVE_STOREFRONT_PORT}/"
check_url "Nginx storefront root" "http://127.0.0.1/" --header "Host: yug-postel.ru"

CURRENT_PHASE="complete"
write_summary success
echo "Storefront-only deploy completed successfully."
