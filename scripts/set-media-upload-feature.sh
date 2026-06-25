#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 || ( "$1" != "true" && "$1" != "false" ) ]]; then
  echo "Usage: $0 <true|false>" >&2
  exit 1
fi

redis_container="$(docker ps --filter 'label=com.docker.compose.service=redis' --format '{{.Names}}' | head -n 1)"
if [[ -z "$redis_container" ]]; then
  echo "Redis container was not found." >&2
  exit 1
fi

docker exec "$redis_container" redis-cli SET catalogue:media:uploads-enabled "$1" >/dev/null
actual="$(docker exec "$redis_container" redis-cli GET catalogue:media:uploads-enabled)"
if [[ "$actual" != "$1" ]]; then
  echo "Feature flag verification failed." >&2
  exit 1
fi
echo "Media uploads enabled: $actual"
