#!/usr/bin/env bash

# shellcheck shell=bash

runtime_set_defaults() {
  : "${DEPLOY_RUNTIME_RELEASES_DIR:=${ROOT_DIR}/releases}"
  : "${DEPLOY_RUNTIME_STATE_DIR:=${ROOT_DIR}/.deploy-state}"
  : "${DEPLOY_RUNTIME_BLUE_API_PORT:=18080}"
  : "${DEPLOY_RUNTIME_BLUE_DIRECTUS_PORT:=18055}"
  : "${DEPLOY_RUNTIME_BLUE_STOREFRONT_PORT:=13000}"
  : "${DEPLOY_RUNTIME_GREEN_API_PORT:=28080}"
  : "${DEPLOY_RUNTIME_GREEN_DIRECTUS_PORT:=28055}"
  : "${DEPLOY_RUNTIME_GREEN_STOREFRONT_PORT:=23000}"
  : "${DEPLOY_SHARED_DOCKER_NETWORK:=eshop-shared}"
  : "${DEPLOY_NGINX_API_UPSTREAM_INCLUDE:=/etc/nginx/includes/eshop-api-upstream.conf}"
  : "${DEPLOY_NGINX_CMS_UPSTREAM_INCLUDE:=/etc/nginx/includes/eshop-cms-upstream.conf}"
  : "${DEPLOY_NGINX_STOREFRONT_UPSTREAM_INCLUDE:=/etc/nginx/includes/eshop-storefront-upstream.conf}"
  : "${DEPLOY_NGINX_TEST_COMMAND:=sudo nginx -t}"
  : "${DEPLOY_NGINX_RELOAD_COMMAND:=sudo systemctl reload nginx}"
  : "${DEPLOY_RUNTIME_OBSERVATION_SECONDS:=15}"
  : "${DEPLOY_RUNTIME_MIN_AVAILABLE_MEMORY_MB:=1024}"
  : "${DEPLOY_RUNTIME_MIN_AVAILABLE_DISK_MB:=1024}"
  : "${DEPLOY_RUNTIME_LOG_DIR:=${DEPLOY_RUNTIME_STATE_DIR}/logs}"
  : "${DEPLOY_RUNTIME_SUMMARY_FILE:=${DEPLOY_RUNTIME_STATE_DIR}/latest-runtime-summary.txt}"
  : "${PUBLIC_DIRECTUS_HEALTHCHECK_URL:=${DIRECTUS_PUBLIC_URL%/}/server/health}"
  if [[ -z "${STOREFRONT_PUBLIC_URL:-}" && -n "${REACT_APP_SITE_URL:-}" ]]; then
    STOREFRONT_PUBLIC_URL="${REACT_APP_SITE_URL}"
  fi
  if [[ -z "${PUBLIC_STOREFRONT_HEALTHCHECK_URL:-}" && -n "${STOREFRONT_PUBLIC_URL:-}" ]]; then
    PUBLIC_STOREFRONT_HEALTHCHECK_URL="${STOREFRONT_PUBLIC_URL%/}/healthz"
  fi
  : "${DEPLOY_RUNTIME_CONTENT_HEALTHCHECK_PATH:=/content/navigation?placement=header}"
  : "${DEPLOY_RUNTIME_API_HEALTHCHECK_PATH:=/health/redis}"
  : "${DEPLOY_RUNTIME_DIRECTUS_HEALTHCHECK_PATH:=/server/health}"
  : "${DEPLOY_RUNTIME_STOREFRONT_HEALTHCHECK_PATH:=/healthz}"
}

runtime_prepare_dirs() {
  mkdir -p \
    "$DEPLOY_RUNTIME_RELEASES_DIR" \
    "$DEPLOY_RUNTIME_STATE_DIR" \
    "$DEPLOY_RUNTIME_LOG_DIR"
}

runtime_state_file() {
  printf '%s\n' "${DEPLOY_RUNTIME_STATE_DIR}/runtime-live.env"
}

runtime_lock_file() {
  printf '%s\n' "${DEPLOY_RUNTIME_STATE_DIR}/runtime-deploy.lock"
}

runtime_current_link() {
  printf '%s\n' "${DEPLOY_RUNTIME_STATE_DIR}/current-live"
}

runtime_previous_link() {
  printf '%s\n' "${DEPLOY_RUNTIME_STATE_DIR}/previous-live"
}

runtime_summary_file() {
  printf '%s\n' "$DEPLOY_RUNTIME_SUMMARY_FILE"
}

runtime_log_file_for() {
  local prefix="$1"
  local release_id="$2"

  printf '%s/%s-%s-%s.log\n' \
    "$DEPLOY_RUNTIME_LOG_DIR" \
    "$prefix" \
    "$release_id" \
    "$(runtime_current_timestamp)"
}

runtime_slot_project() {
  printf 'eshop-%s\n' "$1"
}

runtime_slot_api_port() {
  case "$1" in
    blue) printf '%s\n' "$DEPLOY_RUNTIME_BLUE_API_PORT" ;;
    green) printf '%s\n' "$DEPLOY_RUNTIME_GREEN_API_PORT" ;;
    *)
      echo "Unsupported runtime slot: $1" >&2
      return 1
      ;;
  esac
}

runtime_slot_directus_port() {
  case "$1" in
    blue) printf '%s\n' "$DEPLOY_RUNTIME_BLUE_DIRECTUS_PORT" ;;
    green) printf '%s\n' "$DEPLOY_RUNTIME_GREEN_DIRECTUS_PORT" ;;
    *)
      echo "Unsupported runtime slot: $1" >&2
      return 1
      ;;
  esac
}

runtime_slot_storefront_port() {
  case "$1" in
    blue) printf '%s\n' "$DEPLOY_RUNTIME_BLUE_STOREFRONT_PORT" ;;
    green) printf '%s\n' "$DEPLOY_RUNTIME_GREEN_STOREFRONT_PORT" ;;
    *)
      echo "Unsupported runtime slot: $1" >&2
      return 1
      ;;
  esac
}

runtime_opposite_slot() {
  case "$1" in
    blue) printf 'green\n' ;;
    green) printf 'blue\n' ;;
    *)
      echo "Unsupported runtime slot: $1" >&2
      return 1
      ;;
  esac
}

runtime_release_dir() {
  printf '%s/%s\n' "$DEPLOY_RUNTIME_RELEASES_DIR" "$1"
}

runtime_compose_file() {
  printf '%s\n' "${1}/docker-compose.runtime-slot.yml"
}

runtime_root_compose_file() {
  printf '%s\n' "${ROOT_DIR}/docker-compose.runtime-slot.yml"
}

runtime_compose_has_service() {
  local compose_file="$1"
  local service_name="$2"

  [[ -f "$compose_file" ]] || return 1
  grep -Eq "^[[:space:]]{2}${service_name}:" "$compose_file"
}

runtime_storefront_runtime_enabled() {
  [[ -n "${CURRENT_LIVE_STOREFRONT_PORT:-}" || \
     -n "${PREVIOUS_LIVE_STOREFRONT_PORT:-}" || \
     -n "${STOREFRONT_HOST_PORT:-}" || \
     ( -n "${STOREFRONT_IMAGE_REPOSITORY:-}" && -n "${STOREFRONT_IMAGE_TAG:-}" ) ]]
}

runtime_normalize_state_storefront_ports() {
  if ! runtime_storefront_runtime_enabled; then
    return 0
  fi

  if [[ -z "${CURRENT_LIVE_STOREFRONT_PORT:-}" && -n "${CURRENT_LIVE_SLOT:-}" ]]; then
    CURRENT_LIVE_STOREFRONT_PORT="$(runtime_slot_storefront_port "$CURRENT_LIVE_SLOT")"
  fi

  if [[ -z "${PREVIOUS_LIVE_STOREFRONT_PORT:-}" && -n "${PREVIOUS_LIVE_SLOT:-}" ]]; then
    PREVIOUS_LIVE_STOREFRONT_PORT="$(runtime_slot_storefront_port "$PREVIOUS_LIVE_SLOT")"
  fi
}

runtime_resolved_compose_file() {
  local release_dir="$1"
  local release_compose root_compose

  release_compose="$(runtime_compose_file "$release_dir")"
  root_compose="$(runtime_root_compose_file)"

  if [[ ! -f "$release_compose" ]]; then
    if [[ -f "$root_compose" ]]; then
      printf '%s\n' "$root_compose"
      return 0
    fi

    echo "Missing runtime compose file for release: $release_compose" >&2
    return 1
  fi

  # Older release directories predate the storefront container. When the
  # runtime is storefront-enabled, use the current runtime-slot compose as a
  # compatibility shim so rollback can still recreate the storefront service.
  if runtime_storefront_runtime_enabled && \
     ! runtime_compose_has_service "$release_compose" storefront && \
     [[ -f "$root_compose" ]] && \
     runtime_compose_has_service "$root_compose" storefront; then
    printf '%s\n' "$root_compose"
    return 0
  fi

  printf '%s\n' "$release_compose"
}

runtime_load_state() {
  local state_file

  state_file="$(runtime_state_file)"

  if [[ -f "$state_file" ]]; then
    load_env_file "$state_file"
  fi

  runtime_normalize_state_storefront_ports
}

runtime_write_state() {
  local state_file

  state_file="$(runtime_state_file)"

  cat >"$state_file" <<EOF
CURRENT_LIVE_RELEASE_ID=${CURRENT_LIVE_RELEASE_ID:-}
CURRENT_LIVE_RELEASE_DIR=${CURRENT_LIVE_RELEASE_DIR:-}
CURRENT_LIVE_SLOT=${CURRENT_LIVE_SLOT:-}
CURRENT_LIVE_PROJECT=${CURRENT_LIVE_PROJECT:-}
CURRENT_LIVE_API_PORT=${CURRENT_LIVE_API_PORT:-}
CURRENT_LIVE_DIRECTUS_PORT=${CURRENT_LIVE_DIRECTUS_PORT:-}
CURRENT_LIVE_STOREFRONT_PORT=${CURRENT_LIVE_STOREFRONT_PORT:-}
PREVIOUS_LIVE_RELEASE_ID=${PREVIOUS_LIVE_RELEASE_ID:-}
PREVIOUS_LIVE_RELEASE_DIR=${PREVIOUS_LIVE_RELEASE_DIR:-}
PREVIOUS_LIVE_SLOT=${PREVIOUS_LIVE_SLOT:-}
PREVIOUS_LIVE_PROJECT=${PREVIOUS_LIVE_PROJECT:-}
PREVIOUS_LIVE_API_PORT=${PREVIOUS_LIVE_API_PORT:-}
PREVIOUS_LIVE_DIRECTUS_PORT=${PREVIOUS_LIVE_DIRECTUS_PORT:-}
PREVIOUS_LIVE_STOREFRONT_PORT=${PREVIOUS_LIVE_STOREFRONT_PORT:-}
LAST_DEPLOYED_SHA=${LAST_DEPLOYED_SHA:-}
LAST_DEPLOYED_REF=${LAST_DEPLOYED_REF:-}
LAST_DEPLOY_STATUS=${LAST_DEPLOY_STATUS:-}
LAST_DEPLOY_RUN_ID=${LAST_DEPLOY_RUN_ID:-}
LAST_DEPLOY_AT=${LAST_DEPLOY_AT:-}
EOF

  if [[ -n "${CURRENT_LIVE_RELEASE_DIR:-}" ]]; then
    ln -sfn "$CURRENT_LIVE_RELEASE_DIR" "$(runtime_current_link)"
  fi

  if [[ -n "${PREVIOUS_LIVE_RELEASE_DIR:-}" ]]; then
    ln -sfn "$PREVIOUS_LIVE_RELEASE_DIR" "$(runtime_previous_link)"
  fi
}

runtime_write_upstream_include() {
  local target_file="$1"
  local target_port="$2"

  mkdir -p "$(dirname "$target_file")"
  printf 'proxy_pass http://127.0.0.1:%s;\n' "$target_port" >"$target_file"
}

runtime_read_upstream_port() {
  local target_file="$1"

  if [[ ! -f "$target_file" ]]; then
    return 1
  fi

  sed -n 's/.*127\.0\.0\.1:\([0-9][0-9]*\).*/\1/p' "$target_file" | head -n 1
}

runtime_reload_nginx() {
  bash -lc "$DEPLOY_NGINX_TEST_COMMAND"
  bash -lc "$DEPLOY_NGINX_RELOAD_COMMAND"
}

runtime_current_timestamp() {
  date -u +%Y%m%dT%H%M%SZ
}

runtime_available_memory_mb() {
  awk '/MemAvailable/ { printf "%d\n", $2 / 1024 }' /proc/meminfo
}

runtime_available_disk_mb() {
  df -Pm "$DEPLOY_RUNTIME_RELEASES_DIR" | awk 'NR==2 { print $4 }'
}

runtime_internal_api_url() {
  local port="$1"

  printf 'http://127.0.0.1:%s%s\n' "$port" "$DEPLOY_RUNTIME_API_HEALTHCHECK_PATH"
}

runtime_internal_directus_url() {
  local port="$1"

  printf 'http://127.0.0.1:%s%s\n' "$port" "$DEPLOY_RUNTIME_DIRECTUS_HEALTHCHECK_PATH"
}

runtime_internal_storefront_url() {
  local port="$1"

  printf 'http://127.0.0.1:%s%s\n' "$port" "$DEPLOY_RUNTIME_STOREFRONT_HEALTHCHECK_PATH"
}

runtime_internal_content_url() {
  local port="$1"

  printf 'http://127.0.0.1:%s%s\n' "$port" "$DEPLOY_RUNTIME_CONTENT_HEALTHCHECK_PATH"
}

runtime_public_directus_url() {
  if [[ -n "${PUBLIC_DIRECTUS_HEALTHCHECK_URL:-}" ]]; then
    printf '%s\n' "$PUBLIC_DIRECTUS_HEALTHCHECK_URL"
    return 0
  fi

  if [[ -n "${DIRECTUS_PUBLIC_URL:-}" ]]; then
    printf '%s\n' "${DIRECTUS_PUBLIC_URL%/}/server/health"
    return 0
  fi

  printf '\n'
}

runtime_public_storefront_url() {
  if [[ -n "${PUBLIC_STOREFRONT_HEALTHCHECK_URL:-}" ]]; then
    printf '%s\n' "$PUBLIC_STOREFRONT_HEALTHCHECK_URL"
    return 0
  fi

  if [[ -n "${STOREFRONT_PUBLIC_URL:-}" ]]; then
    printf '%s\n' "${STOREFRONT_PUBLIC_URL%/}/healthz"
    return 0
  fi

  if [[ -n "${REACT_APP_SITE_URL:-}" ]]; then
    printf '%s\n' "${REACT_APP_SITE_URL%/}/healthz"
    return 0
  fi

  printf '\n'
}

runtime_write_summary() {
  local summary_file

  summary_file="$(runtime_summary_file)"
  mkdir -p "$(dirname "$summary_file")"
  printf '%s\n' "$*" >"$summary_file"
}
