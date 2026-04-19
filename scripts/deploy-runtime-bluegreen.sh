#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"
COMPOSE_FILE="$ROOT_DIR/docker-compose.prod.yml"
DEPLOY_REF="${DEPLOY_REF:-main}"
DEPLOY_SHA="${DEPLOY_SHA:-}"
DEPLOY_RUN_ID="${DEPLOY_RUN_ID:-manual}"
API_IMAGE_REPOSITORY="${API_IMAGE_REPOSITORY:-}"
API_IMAGE_TAG="${API_IMAGE_TAG:-}"
WAIT_TIMEOUT_SECONDS="${WAIT_TIMEOUT_SECONDS:-180}"

# shellcheck source=scripts/lib/env-file.sh
source "$ROOT_DIR/scripts/lib/env-file.sh"
# shellcheck source=scripts/lib/runtime-release.sh
source "$ROOT_DIR/scripts/lib/runtime-release.sh"

usage() {
  cat <<'EOF'
Usage:
  ./scripts/deploy-runtime-bluegreen.sh \
    [--env-file <path>] \
    [--compose-file <path>] \
    [--deploy-ref <git-ref>] \
    [--deploy-sha <git-sha>] \
    [--api-image-repository <image>] \
    [--api-image-tag <tag>] \
    [--run-id <id>] \
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
    --deploy-ref)
      DEPLOY_REF="$2"
      shift 2
      ;;
    --deploy-sha)
      DEPLOY_SHA="$2"
      shift 2
      ;;
    --api-image-repository)
      API_IMAGE_REPOSITORY="$2"
      shift 2
      ;;
    --api-image-tag)
      API_IMAGE_TAG="$2"
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

require_env API_IMAGE_REPOSITORY "runtime deploy arguments"
require_env API_IMAGE_TAG "runtime deploy arguments"
require_env DIRECTUS_PUBLIC_URL "$ENV_FILE"
require_env PUBLIC_API_HEALTHCHECK_URL "$ENV_FILE"
require_env PUBLIC_CONTENT_HEALTHCHECK_URL "$ENV_FILE"

exec 9>"$(runtime_lock_file)"
if ! flock -n 9; then
  echo "Another runtime deploy or rollback is already in progress." >&2
  exit 1
fi

CURRENT_PHASE="initializing"
CUTOVER_APPLIED=false
STATE_UPDATED=false
CANDIDATE_SLOT=""
CANDIDATE_PROJECT=""
CANDIDATE_RELEASE_DIR=""
CANDIDATE_API_PORT=""
CANDIDATE_DIRECTUS_PORT=""
TARGET_SHA=""
LOG_FILE=""
API_INCLUDE_BACKUP=""
CMS_INCLUDE_BACKUP=""

legacy_compose() {
  if docker compose version >/dev/null 2>&1; then
    docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
  elif command -v docker-compose >/dev/null 2>&1; then
    docker-compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
  else
    echo "Docker Compose not found." >&2
    exit 1
  fi
}

candidate_compose() {
  local compose_file

  compose_file="$(runtime_compose_file "$CANDIDATE_RELEASE_DIR")"

  if docker compose version >/dev/null 2>&1; then
    docker compose \
      --project-name "$CANDIDATE_PROJECT" \
      --env-file "$ENV_FILE" \
      -f "$compose_file" \
      "$@"
  elif command -v docker-compose >/dev/null 2>&1; then
    docker-compose \
      --project-name "$CANDIDATE_PROJECT" \
      --env-file "$ENV_FILE" \
      -f "$compose_file" \
      "$@"
  else
    echo "Docker Compose not found." >&2
    exit 1
  fi
}

write_failure_summary() {
  local summary_file timestamp

  timestamp="$(runtime_current_timestamp)"
  summary_file="$(runtime_summary_file)"
  mkdir -p "$(dirname "$summary_file")"
  cat >"$summary_file" <<EOF
status=failure
phase=$CURRENT_PHASE
deploy_ref=$DEPLOY_REF
deploy_sha=${TARGET_SHA:-unknown}
run_id=$DEPLOY_RUN_ID
timestamp=$timestamp
candidate_slot=${CANDIDATE_SLOT:-}
candidate_project=${CANDIDATE_PROJECT:-}
candidate_release_dir=${CANDIDATE_RELEASE_DIR:-}
log_file=${LOG_FILE:-}
EOF
}

restore_previous_upstreams() {
  if [[ -n "$API_INCLUDE_BACKUP" && -f "$API_INCLUDE_BACKUP" ]]; then
    cp "$API_INCLUDE_BACKUP" "$DEPLOY_NGINX_API_UPSTREAM_INCLUDE"
  fi

  if [[ -n "$CMS_INCLUDE_BACKUP" && -f "$CMS_INCLUDE_BACKUP" ]]; then
    cp "$CMS_INCLUDE_BACKUP" "$DEPLOY_NGINX_CMS_UPSTREAM_INCLUDE"
  fi

  runtime_reload_nginx
}

on_error() {
  local exit_code="$1"
  local line_number="$2"

  echo "Runtime deploy failed during phase '$CURRENT_PHASE' at line ${line_number}." >&2

  if [[ -n "$CANDIDATE_RELEASE_DIR" ]]; then
    candidate_compose ps || true
    candidate_compose logs --tail=200 api directus || true
  fi

  if [[ "$CUTOVER_APPLIED" == "true" ]]; then
    echo "Restoring previous nginx upstreams..." >&2
    restore_previous_upstreams || true
  fi

  if [[ "$STATE_UPDATED" == "true" ]]; then
    CURRENT_LIVE_RELEASE_ID="${PRE_CUTOVER_CURRENT_LIVE_RELEASE_ID:-}"
    CURRENT_LIVE_RELEASE_DIR="${PRE_CUTOVER_CURRENT_LIVE_RELEASE_DIR:-}"
    CURRENT_LIVE_SLOT="${PRE_CUTOVER_CURRENT_LIVE_SLOT:-}"
    CURRENT_LIVE_PROJECT="${PRE_CUTOVER_CURRENT_LIVE_PROJECT:-}"
    CURRENT_LIVE_API_PORT="${PRE_CUTOVER_CURRENT_LIVE_API_PORT:-}"
    CURRENT_LIVE_DIRECTUS_PORT="${PRE_CUTOVER_CURRENT_LIVE_DIRECTUS_PORT:-}"
    PREVIOUS_LIVE_RELEASE_ID="${PRE_CUTOVER_PREVIOUS_LIVE_RELEASE_ID:-}"
    PREVIOUS_LIVE_RELEASE_DIR="${PRE_CUTOVER_PREVIOUS_LIVE_RELEASE_DIR:-}"
    PREVIOUS_LIVE_SLOT="${PRE_CUTOVER_PREVIOUS_LIVE_SLOT:-}"
    PREVIOUS_LIVE_PROJECT="${PRE_CUTOVER_PREVIOUS_LIVE_PROJECT:-}"
    PREVIOUS_LIVE_API_PORT="${PRE_CUTOVER_PREVIOUS_LIVE_API_PORT:-}"
    PREVIOUS_LIVE_DIRECTUS_PORT="${PRE_CUTOVER_PREVIOUS_LIVE_DIRECTUS_PORT:-}"
  else
    runtime_load_state || true
  fi
  LAST_DEPLOYED_SHA="${TARGET_SHA:-$DEPLOY_SHA}"
  LAST_DEPLOYED_REF="$DEPLOY_REF"
  LAST_DEPLOY_STATUS="failed"
  LAST_DEPLOY_RUN_ID="$DEPLOY_RUN_ID"
  LAST_DEPLOY_AT="$(runtime_current_timestamp)"
  runtime_write_state || true
  write_failure_summary
  exit "$exit_code"
}

trap 'on_error $? $LINENO' ERR

fetch_target_sha() {
  local fetched_sha

  git fetch --prune origin "$DEPLOY_REF"
  fetched_sha="$(git rev-parse FETCH_HEAD)"

  if [[ -n "$DEPLOY_SHA" ]]; then
    git rev-parse --verify "${DEPLOY_SHA}^{commit}" >/dev/null 2>&1
    if [[ "$fetched_sha" != "$DEPLOY_SHA" ]]; then
      echo "Fetched ref ${DEPLOY_REF} resolved to ${fetched_sha}, expected ${DEPLOY_SHA}." >&2
      exit 1
    fi
    printf '%s\n' "$DEPLOY_SHA"
    return 0
  fi

  printf '%s\n' "$fetched_sha"
}

materialize_release_dir() {
  local release_dir="$1"
  local release_sha="$2"
  local existing_sha

  if [[ -e "$release_dir" ]]; then
    existing_sha="$(git -C "$release_dir" rev-parse HEAD)"
    if [[ "$existing_sha" != "$release_sha" ]]; then
      echo "Release directory ${release_dir} already exists with ${existing_sha}, expected ${release_sha}." >&2
      exit 1
    fi
    return 0
  fi

  git worktree add --detach "$release_dir" "$release_sha"
}

export_candidate_runtime_env() {
  export API_IMAGE_REPOSITORY
  export API_IMAGE_TAG
  export RUNTIME_ENV_FILE="$ENV_FILE"
  export RUNTIME_RELEASE_DIR="$CANDIDATE_RELEASE_DIR"
  export RUNTIME_RELEASE_ID="$TARGET_SHA"
  export RUNTIME_SLOT="$CANDIDATE_SLOT"
  export RUNTIME_API_HOST_PORT="$CANDIDATE_API_PORT"
  export RUNTIME_DIRECTUS_HOST_PORT="$CANDIDATE_DIRECTUS_PORT"
  export DEPLOY_SHARED_DOCKER_NETWORK
}

ensure_host_capacity() {
  local available_memory available_disk

  available_memory="$(runtime_available_memory_mb)"
  available_disk="$(runtime_available_disk_mb)"

  echo "Available memory: ${available_memory} MB"
  echo "Available disk: ${available_disk} MB"

  if (( available_memory < DEPLOY_RUNTIME_MIN_AVAILABLE_MEMORY_MB )); then
    echo "Available memory ${available_memory} MB is below the required ${DEPLOY_RUNTIME_MIN_AVAILABLE_MEMORY_MB} MB." >&2
    exit 1
  fi

  if (( available_disk < DEPLOY_RUNTIME_MIN_AVAILABLE_DISK_MB )); then
    echo "Available disk ${available_disk} MB is below the required ${DEPLOY_RUNTIME_MIN_AVAILABLE_DISK_MB} MB." >&2
    exit 1
  fi
}

check_keycloak_dependency() {
  local issuer_discovery_url

  issuer_discovery_url="${DIRECTUS_AUTH_KEYCLOAK_ISSUER_URL:-}"
  if [[ -z "$issuer_discovery_url" && -n "${KEYCLOAK_ISSUER_URI:-}" ]]; then
    issuer_discovery_url="${KEYCLOAK_ISSUER_URI%/}/.well-known/openid-configuration"
  fi

  if [[ -z "$issuer_discovery_url" ]]; then
    echo "Skipping Keycloak discovery preflight because no issuer URL is configured."
    return 0
  fi

  curl --silent --show-error --fail --max-time 10 "$issuer_discovery_url" >/dev/null
  echo "Keycloak discovery preflight passed: $issuer_discovery_url"
}

ensure_nginx_cutover_contract() {
  if [[ ! -f "$DEPLOY_NGINX_API_UPSTREAM_INCLUDE" ]]; then
    echo "Missing API nginx upstream include: $DEPLOY_NGINX_API_UPSTREAM_INCLUDE" >&2
    exit 1
  fi

  if [[ ! -f "$DEPLOY_NGINX_CMS_UPSTREAM_INCLUDE" ]]; then
    echo "Missing CMS nginx upstream include: $DEPLOY_NGINX_CMS_UPSTREAM_INCLUDE" >&2
    exit 1
  fi
}

CURRENT_PHASE="preflight"
runtime_load_state
PRE_CUTOVER_CURRENT_LIVE_RELEASE_ID="${CURRENT_LIVE_RELEASE_ID:-}"
PRE_CUTOVER_CURRENT_LIVE_RELEASE_DIR="${CURRENT_LIVE_RELEASE_DIR:-}"
PRE_CUTOVER_CURRENT_LIVE_SLOT="${CURRENT_LIVE_SLOT:-}"
PRE_CUTOVER_CURRENT_LIVE_PROJECT="${CURRENT_LIVE_PROJECT:-}"
PRE_CUTOVER_CURRENT_LIVE_API_PORT="${CURRENT_LIVE_API_PORT:-}"
PRE_CUTOVER_CURRENT_LIVE_DIRECTUS_PORT="${CURRENT_LIVE_DIRECTUS_PORT:-}"
PRE_CUTOVER_PREVIOUS_LIVE_RELEASE_ID="${PREVIOUS_LIVE_RELEASE_ID:-}"
PRE_CUTOVER_PREVIOUS_LIVE_RELEASE_DIR="${PREVIOUS_LIVE_RELEASE_DIR:-}"
PRE_CUTOVER_PREVIOUS_LIVE_SLOT="${PREVIOUS_LIVE_SLOT:-}"
PRE_CUTOVER_PREVIOUS_LIVE_PROJECT="${PREVIOUS_LIVE_PROJECT:-}"
PRE_CUTOVER_PREVIOUS_LIVE_API_PORT="${PREVIOUS_LIVE_API_PORT:-}"
PRE_CUTOVER_PREVIOUS_LIVE_DIRECTUS_PORT="${PREVIOUS_LIVE_DIRECTUS_PORT:-}"
ensure_host_capacity
check_keycloak_dependency
ensure_nginx_cutover_contract

TARGET_SHA="$(fetch_target_sha)"
DEPLOY_SHA="$TARGET_SHA"
LOG_FILE="$(runtime_log_file_for "runtime-deploy" "$TARGET_SHA")"
exec > >(tee -a "$LOG_FILE") 2>&1

echo "Starting runtime blue-green deploy."
echo "Deploy ref: $DEPLOY_REF"
echo "Requested SHA: ${DEPLOY_SHA:-auto}"
echo "API image: ${API_IMAGE_REPOSITORY}:${API_IMAGE_TAG}"

CURRENT_PHASE="materialize-release"
CANDIDATE_RELEASE_DIR="$(runtime_release_dir "$TARGET_SHA")"
materialize_release_dir "$CANDIDATE_RELEASE_DIR" "$TARGET_SHA"
echo "Release directory ready: $CANDIDATE_RELEASE_DIR"

CURRENT_PHASE="bootstrap-shared-infra"
docker network inspect "$DEPLOY_SHARED_DOCKER_NETWORK" >/dev/null 2>&1 || docker network create "$DEPLOY_SHARED_DOCKER_NETWORK" >/dev/null
legacy_compose up -d postgres redis

if [[ -n "${CURRENT_LIVE_SLOT:-}" ]]; then
  CANDIDATE_SLOT="$(runtime_opposite_slot "$CURRENT_LIVE_SLOT")"
else
  CANDIDATE_SLOT="blue"
fi

CANDIDATE_PROJECT="$(runtime_slot_project "$CANDIDATE_SLOT")"
CANDIDATE_API_PORT="$(runtime_slot_api_port "$CANDIDATE_SLOT")"
CANDIDATE_DIRECTUS_PORT="$(runtime_slot_directus_port "$CANDIDATE_SLOT")"
export_candidate_runtime_env

echo "Candidate slot: $CANDIDATE_SLOT"
echo "Candidate project: $CANDIDATE_PROJECT"
echo "Candidate API port: $CANDIDATE_API_PORT"
echo "Candidate Directus port: $CANDIDATE_DIRECTUS_PORT"

CURRENT_PHASE="start-candidate"
candidate_compose down --remove-orphans >/dev/null 2>&1 || true
candidate_compose pull api directus
candidate_compose up -d

CURRENT_PHASE="candidate-internal-health"
bash "$ROOT_DIR/scripts/check-stack-health.sh" \
  --env-file "$ENV_FILE" \
  --compose-file "$COMPOSE_FILE" \
  --api-url "$(runtime_internal_api_url "$CANDIDATE_API_PORT")" \
  --directus-url "$(runtime_internal_directus_url "$CANDIDATE_DIRECTUS_PORT")" \
  --content-url "$(runtime_internal_content_url "$CANDIDATE_API_PORT")" \
  --skip-public \
  --timeout-seconds "$WAIT_TIMEOUT_SECONDS"

CURRENT_PHASE="cutover"
API_INCLUDE_BACKUP="$(mktemp "${DEPLOY_RUNTIME_STATE_DIR}/api-upstream.XXXXXX.bak")"
CMS_INCLUDE_BACKUP="$(mktemp "${DEPLOY_RUNTIME_STATE_DIR}/cms-upstream.XXXXXX.bak")"
cp "$DEPLOY_NGINX_API_UPSTREAM_INCLUDE" "$API_INCLUDE_BACKUP"
cp "$DEPLOY_NGINX_CMS_UPSTREAM_INCLUDE" "$CMS_INCLUDE_BACKUP"
runtime_write_upstream_include "$DEPLOY_NGINX_API_UPSTREAM_INCLUDE" "$CANDIDATE_API_PORT"
runtime_write_upstream_include "$DEPLOY_NGINX_CMS_UPSTREAM_INCLUDE" "$CANDIDATE_DIRECTUS_PORT"
runtime_reload_nginx
CUTOVER_APPLIED=true

CURRENT_PHASE="public-post-cutover-health"
bash "$ROOT_DIR/scripts/check-stack-health.sh" \
  --env-file "$ENV_FILE" \
  --compose-file "$COMPOSE_FILE" \
  --api-url "$(runtime_internal_api_url "$CANDIDATE_API_PORT")" \
  --directus-url "$(runtime_internal_directus_url "$CANDIDATE_DIRECTUS_PORT")" \
  --content-url "$(runtime_internal_content_url "$CANDIDATE_API_PORT")" \
  --public-api-url "$PUBLIC_API_HEALTHCHECK_URL" \
  --public-directus-url "$(runtime_public_directus_url)" \
  --public-content-url "$PUBLIC_CONTENT_HEALTHCHECK_URL" \
  --timeout-seconds "$WAIT_TIMEOUT_SECONDS"

CURRENT_PHASE="observation-window"
sleep "$DEPLOY_RUNTIME_OBSERVATION_SECONDS"
bash "$ROOT_DIR/scripts/check-stack-health.sh" \
  --env-file "$ENV_FILE" \
  --compose-file "$COMPOSE_FILE" \
  --api-url "$(runtime_internal_api_url "$CANDIDATE_API_PORT")" \
  --directus-url "$(runtime_internal_directus_url "$CANDIDATE_DIRECTUS_PORT")" \
  --content-url "$(runtime_internal_content_url "$CANDIDATE_API_PORT")" \
  --public-api-url "$PUBLIC_API_HEALTHCHECK_URL" \
  --public-directus-url "$(runtime_public_directus_url)" \
  --public-content-url "$PUBLIC_CONTENT_HEALTHCHECK_URL" \
  --timeout-seconds "$WAIT_TIMEOUT_SECONDS"

CURRENT_LIVE_RELEASE_ID="${CURRENT_LIVE_RELEASE_ID:-}"
CURRENT_LIVE_RELEASE_DIR="${CURRENT_LIVE_RELEASE_DIR:-}"
CURRENT_LIVE_SLOT="${CURRENT_LIVE_SLOT:-}"
CURRENT_LIVE_PROJECT="${CURRENT_LIVE_PROJECT:-}"
CURRENT_LIVE_API_PORT="${CURRENT_LIVE_API_PORT:-}"
CURRENT_LIVE_DIRECTUS_PORT="${CURRENT_LIVE_DIRECTUS_PORT:-}"

PREVIOUS_LIVE_RELEASE_ID="$CURRENT_LIVE_RELEASE_ID"
PREVIOUS_LIVE_RELEASE_DIR="$CURRENT_LIVE_RELEASE_DIR"
PREVIOUS_LIVE_SLOT="$CURRENT_LIVE_SLOT"
PREVIOUS_LIVE_PROJECT="$CURRENT_LIVE_PROJECT"
PREVIOUS_LIVE_API_PORT="$CURRENT_LIVE_API_PORT"
PREVIOUS_LIVE_DIRECTUS_PORT="$CURRENT_LIVE_DIRECTUS_PORT"

CURRENT_LIVE_RELEASE_ID="$TARGET_SHA"
CURRENT_LIVE_RELEASE_DIR="$CANDIDATE_RELEASE_DIR"
CURRENT_LIVE_SLOT="$CANDIDATE_SLOT"
CURRENT_LIVE_PROJECT="$CANDIDATE_PROJECT"
CURRENT_LIVE_API_PORT="$CANDIDATE_API_PORT"
CURRENT_LIVE_DIRECTUS_PORT="$CANDIDATE_DIRECTUS_PORT"
LAST_DEPLOYED_SHA="$TARGET_SHA"
LAST_DEPLOYED_REF="$DEPLOY_REF"
LAST_DEPLOY_STATUS="success"
LAST_DEPLOY_RUN_ID="$DEPLOY_RUN_ID"
LAST_DEPLOY_AT="$(runtime_current_timestamp)"
runtime_write_state
STATE_UPDATED=true

CURRENT_PHASE="state-verification"
bash "$ROOT_DIR/scripts/check-stack-health.sh" \
  --env-file "$ENV_FILE" \
  --compose-file "$COMPOSE_FILE" \
  --verify-runtime-state \
  --timeout-seconds "$WAIT_TIMEOUT_SECONDS"

CURRENT_PHASE="retire-previous-live"
if [[ -n "${PREVIOUS_LIVE_PROJECT:-}" && "$PREVIOUS_LIVE_PROJECT" != "$CANDIDATE_PROJECT" ]]; then
  PREVIOUS_RELEASE_DIR="$PREVIOUS_LIVE_RELEASE_DIR"
  if [[ -n "$PREVIOUS_RELEASE_DIR" && -f "$(runtime_compose_file "$PREVIOUS_RELEASE_DIR")" ]]; then
    CANDIDATE_PROJECT="$PREVIOUS_LIVE_PROJECT"
    CANDIDATE_RELEASE_DIR="$PREVIOUS_RELEASE_DIR"
    export_candidate_runtime_env
    candidate_compose down --remove-orphans || true
  fi
fi

if [[ -z "${PREVIOUS_LIVE_SLOT:-}" ]]; then
  legacy_compose stop api directus >/dev/null 2>&1 || true
fi

CURRENT_PHASE="complete"
cat >"$(runtime_summary_file)" <<EOF
status=success
phase=$CURRENT_PHASE
deploy_ref=$DEPLOY_REF
deploy_sha=$TARGET_SHA
run_id=$DEPLOY_RUN_ID
slot=$CURRENT_LIVE_SLOT
project=$CURRENT_LIVE_PROJECT
release_dir=$CURRENT_LIVE_RELEASE_DIR
api_port=$CURRENT_LIVE_API_PORT
directus_port=$CURRENT_LIVE_DIRECTUS_PORT
log_file=$LOG_FILE
EOF

echo "Runtime blue-green deploy completed successfully."
