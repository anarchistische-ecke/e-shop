#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
FIXTURE_DIR="$(mktemp -d)"

cleanup() {
  rm -rf "$FIXTURE_DIR"
}

trap cleanup EXIT

mkdir -p \
  "$FIXTURE_DIR/bin" \
  "$FIXTURE_DIR/nginx" \
  "$FIXTURE_DIR/release" \
  "$FIXTURE_DIR/scripts/lib" \
  "$FIXTURE_DIR/state"

cp "$ROOT_DIR/scripts/check-stack-health.sh" "$FIXTURE_DIR/scripts/check-stack-health.sh"
cp "$ROOT_DIR/scripts/storefront-content-consistency-check.mjs" "$FIXTURE_DIR/scripts/storefront-content-consistency-check.mjs"
cp "$ROOT_DIR/scripts/lib/env-file.sh" "$FIXTURE_DIR/scripts/lib/env-file.sh"
cp "$ROOT_DIR/scripts/lib/runtime-release.sh" "$FIXTURE_DIR/scripts/lib/runtime-release.sh"

cat >"$FIXTURE_DIR/bin/curl" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
chmod +x "$FIXTURE_DIR/bin/curl"

git -C "$FIXTURE_DIR/release" init --quiet
git -C "$FIXTURE_DIR/release" \
  -c user.name=fixture \
  -c user.email=fixture@example.invalid \
  commit --quiet --allow-empty -m fixture
release_sha="$(git -C "$FIXTURE_DIR/release" rev-parse HEAD)"

cat >"$FIXTURE_DIR/.env" <<EOF
DIRECTUS_PUBLIC_URL=https://cms.example.invalid
DEPLOY_RUNTIME_STATE_DIR=$FIXTURE_DIR/state
DEPLOY_RUNTIME_RELEASES_DIR=$FIXTURE_DIR/releases
DEPLOY_NGINX_API_UPSTREAM_INCLUDE=$FIXTURE_DIR/nginx/api.conf
DEPLOY_NGINX_CMS_UPSTREAM_INCLUDE=$FIXTURE_DIR/nginx/cms.conf
DEPLOY_NGINX_STOREFRONT_UPSTREAM_INCLUDE=$FIXTURE_DIR/nginx/storefront.conf
EOF

cat >"$FIXTURE_DIR/state/runtime-live.env" <<EOF
CURRENT_LIVE_RELEASE_ID=$release_sha
CURRENT_LIVE_RELEASE_DIR=$FIXTURE_DIR/release
CURRENT_LIVE_SLOT=blue
CURRENT_LIVE_PROJECT=eshop-blue
CURRENT_LIVE_API_PORT=18080
CURRENT_LIVE_DIRECTUS_PORT=18055
CURRENT_LIVE_STOREFRONT_PORT=13000
EOF

printf 'proxy_pass http://127.0.0.1:18080;\n' >"$FIXTURE_DIR/nginx/api.conf"
printf 'proxy_pass http://127.0.0.1:18055;\n' >"$FIXTURE_DIR/nginx/cms.conf"
printf 'proxy_pass http://127.0.0.1:13000;\n' >"$FIXTURE_DIR/nginx/storefront.conf"

PATH="$FIXTURE_DIR/bin:$PATH" \
  bash "$FIXTURE_DIR/scripts/check-stack-health.sh" \
    --env-file "$FIXTURE_DIR/.env" \
    --compose-file "$ROOT_DIR/docker-compose.prod.yml" \
    --skip-public \
    --verify-runtime-state \
    --expected-release-id "$release_sha" >/dev/null

if PATH="$FIXTURE_DIR/bin:$PATH" \
  bash "$FIXTURE_DIR/scripts/check-stack-health.sh" \
    --env-file "$FIXTURE_DIR/.env" \
    --compose-file "$ROOT_DIR/docker-compose.prod.yml" \
    --skip-public \
    --verify-runtime-state \
    --expected-release-id deadbeef >"$FIXTURE_DIR/drift-output.txt" 2>&1; then
  echo "Expected release drift verification to fail." >&2
  exit 1
fi

grep -F "Live release drift detected: production serves ${release_sha}, expected deadbeef." \
  "$FIXTURE_DIR/drift-output.txt" >/dev/null

echo "check-stack-health release drift test passed."
