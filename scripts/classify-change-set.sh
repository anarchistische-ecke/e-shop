#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BASE_REF=""
HEAD_REF="HEAD"

usage() {
  cat <<'EOF'
Usage:
  ./scripts/classify-change-set.sh [--base-ref <git-ref>] [--head-ref <git-ref>]
EOF
}

join_csv() {
  local first=true
  local item

  for item in "$@"; do
    if [[ "$first" == "true" ]]; then
      printf '%s' "$item"
      first=false
    else
      printf ',%s' "$item"
    fi
  done

  printf '\n'
}

print_csv_output() {
  local key="$1"
  shift

  if (( $# == 0 )); then
    printf '%s=\n' "$key"
    return 0
  fi

  printf '%s=%s\n' "$key" "$(join_csv "$@")"
}

is_destructive_path() {
  case "$1" in
    api/src/main/resources/db/migration/*) return 0 ;;
    directus/schema/*) return 0 ;;
    directus/seed/*) return 0 ;;
    scripts/directus-*) return 0 ;;
    docker-compose.prod.yml) return 0 ;;
    docker-compose.runtime-slot.yml) return 0 ;;
    *) return 1 ;;
  esac
}

is_non_runtime_path() {
  case "$1" in
    .github/*) return 0 ;;
    docs/*) return 0 ;;
    README|README.*) return 0 ;;
    ops/nginx/*) return 0 ;;
    scripts/check-stack-health.sh) return 0 ;;
    scripts/classify-change-set.sh) return 0 ;;
    scripts/deploy-*) return 0 ;;
    scripts/rollback-*) return 0 ;;
    scripts/lib/*) return 0 ;;
    .gitignore|.dockerignore) return 0 ;;
    *) return 1 ;;
  esac
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base-ref)
      BASE_REF="$2"
      shift 2
      ;;
    --head-ref)
      HEAD_REF="$2"
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

cd "$ROOT_DIR"

if [[ -z "$BASE_REF" ]]; then
  if git rev-parse --verify "${HEAD_REF}^" >/dev/null 2>&1; then
    BASE_REF="${HEAD_REF}^"
  else
    BASE_REF="$(git hash-object -t tree /dev/null)"
  fi
fi

changed_files=()
while IFS= read -r path; do
  changed_files+=("$path")
done < <(git diff --name-only "$BASE_REF" "$HEAD_REF")

classification="no-runtime-change"
runtime_safe="true"
should_deploy="false"
destructive_count=0
runtime_count=0
destructive_files=()
runtime_files=()

for path in "${changed_files[@]}"; do
  if is_destructive_path "$path"; then
    destructive_files+=("$path")
    destructive_count=$((destructive_count + 1))
    classification="destructive"
    runtime_safe="false"
    should_deploy="false"
    continue
  fi

  if ! is_non_runtime_path "$path"; then
    runtime_files+=("$path")
    runtime_count=$((runtime_count + 1))
    if [[ "$classification" != "destructive" ]]; then
      classification="runtime-safe"
      should_deploy="true"
    fi
  fi
done

printf 'classification=%s\n' "$classification"
printf 'runtime_safe=%s\n' "$runtime_safe"
printf 'should_deploy=%s\n' "$should_deploy"
printf 'changed_files_count=%s\n' "${#changed_files[@]}"
printf 'runtime_files_count=%s\n' "$runtime_count"
printf 'destructive_files_count=%s\n' "$destructive_count"
if (( ${#changed_files[@]} > 0 )); then
  print_csv_output changed_files "${changed_files[@]}"
else
  print_csv_output changed_files
fi

if (( runtime_count > 0 )); then
  print_csv_output runtime_files "${runtime_files[@]}"
else
  print_csv_output runtime_files
fi

if (( destructive_count > 0 )); then
  print_csv_output destructive_files "${destructive_files[@]}"
else
  print_csv_output destructive_files
fi
