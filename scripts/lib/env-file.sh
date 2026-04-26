#!/usr/bin/env bash

# shellcheck shell=bash

trim_ascii_whitespace() {
  local value="$1"

  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"

  printf '%s' "$value"
}

strip_surrounding_quotes() {
  local value="$1"
  local first_char last_char

  if [[ ${#value} -lt 2 ]]; then
    printf '%s' "$value"
    return 0
  fi

  first_char="${value:0:1}"
  last_char="${value: -1}"

  if [[ "$first_char" == "$last_char" && ( "$first_char" == '"' || "$first_char" == "'" ) ]]; then
    printf '%s' "${value:1:${#value}-2}"
    return 0
  fi

  printf '%s' "$value"
}

parse_env_assignment() {
  local env_file="$1"
  local line_number="$2"
  local assignment="$3"
  local key value

  key="$(trim_ascii_whitespace "${assignment%%=*}")"
  value="${assignment#*=}"
  value="${value%$'\r'}"

  if [[ ! "$key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
    echo "Invalid env key on line ${line_number} in ${env_file}: ${key}" >&2
    return 1
  fi

  value="$(strip_surrounding_quotes "$value")"

  if [[ "$value" == *\<* && "$value" == *\>* ]]; then
    echo "Placeholder value is not allowed for ${key} on line ${line_number} in ${env_file}." >&2
    return 1
  fi

  ENV_PARSE_KEY="$key"
  ENV_PARSE_VALUE="$value"
}

load_env_file() {
  local env_file="$1"
  local line line_number

  if [[ ! -f "$env_file" ]]; then
    echo "Missing env file: ${env_file}" >&2
    return 1
  fi

  line_number=0

  while IFS= read -r line || [[ -n "$line" ]]; do
    line_number=$((line_number + 1))

    line="${line%$'\r'}"

    if [[ -z "$(trim_ascii_whitespace "$line")" ]]; then
      continue
    fi

    if [[ "$line" =~ ^[[:space:]]*# ]]; then
      continue
    fi

    if [[ "$line" != *"="* ]]; then
      echo "Malformed env assignment on line ${line_number} in ${env_file}: ${line}" >&2
      return 1
    fi

    parse_env_assignment "$env_file" "$line_number" "$line" || return 1

    printf -v "$ENV_PARSE_KEY" '%s' "$ENV_PARSE_VALUE"
    export "$ENV_PARSE_KEY"
  done <"$env_file"
}

require_env() {
  local key="$1"
  local context="${2:-environment}"

  if [[ -z "${!key:-}" ]]; then
    echo "Missing required variable ${key} in ${context}." >&2
    return 1
  fi
}

resolve_env_file_path() {
  local candidate="$1"

  case "$candidate" in
    /*) printf '%s\n' "$candidate" ;;
    *) printf '%s\n' "$PWD/$candidate" ;;
  esac
}
