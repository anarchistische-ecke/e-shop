# Single-VM Production Bring-Up

This runbook covers only the one-time first blue-green bootstrap on the production VM after the VM itself has already been prepared for the current CI/CD contract.

Use this document only for the current operating model:

- one VM
- production only
- no real staging target yet

Before continuing, complete [production-vm-preparation.md](./production-vm-preparation.md) exactly.

Do not point the `staging` GitHub Environment or `deploy-staging-runtime.yml` at the production VM.

## What This Runbook Does

After you complete this runbook:

- the first release of the rewritten pipeline will be bootstrapped manually on the VM
- nginx will point production traffic at one of the blue-green runtime slots
- `.deploy-state/runtime-live.env` will exist and record the live slot
- later runtime-safe deploys from `main` can use `.github/workflows/deploy-production-runtime.yml`

## What This Runbook Does Not Do

- it does not prepare the VM operating system
- it does not install Docker, nginx, or Git
- it does not create the deploy user or GitHub Actions SSH key
- it does not configure the GitHub Environments
- it does not make `deploy-production-destructive.yml` usable in the current production-only single-VM setup

## Prerequisites

Before touching production, require all of these:

1. [production-vm-preparation.md](./production-vm-preparation.md) is complete.
2. The branch that contains the CI/CD rewrite is pushed to GitHub.
3. `.github/workflows/backend-ci.yml` ran successfully for that branch.
4. Current production API, Directus, and Keycloak are healthy.
5. You can SSH into the production VM as the deploy user.
6. You have a temporary GitHub token that can read and write the `ghcr.io/<repo-owner>/eshop-api` package for the one-time bootstrap image push.

## Step 1: Confirm The Prepared VM Baseline

SSH to the VM as the deploy user and capture the exact deploy path:

```bash
cd /srv/eshop
export PROD_DIR="$(pwd)"
echo "$PROD_DIR"
```

If your real deploy path is different, use that path instead and keep it consistent in every later command.

Run the baseline checks:

```bash
cd "$PROD_DIR"
command -v git
command -v docker
command -v curl
command -v flock
command -v nginx
docker compose version
docker ps >/dev/null
sudo -n nginx -t
git remote -v
git ls-remote origin HEAD
test -f .env
test -f /etc/nginx/includes/eshop-api-upstream.conf
test -f /etc/nginx/includes/eshop-cms-upstream.conf
ls -ld releases .deploy-state .deploy-state/logs backups/directus
git status --short
```

Required result:

- every command succeeds
- `sudo -n nginx -t` does not prompt for a password
- `git ls-remote origin HEAD` succeeds without interactive auth
- `git status --short` is empty

If any check fails, stop and fix the VM using [production-vm-preparation.md](./production-vm-preparation.md).

## Step 2: Verify Current Production Health

Run:

```bash
curl -i https://api.yug-postel.ru/health/redis
curl -i https://cms.yug-postel.ru/server/health
curl -i https://auth.yug-postel.ru/realms/yug-postel/.well-known/openid-configuration
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
```

Required result:

- API health returns `200`
- CMS health returns `200`
- Keycloak discovery returns `200`
- API, Directus, Postgres, and Redis containers are running

## Step 3: Back Up The Current Production Config Files

Run:

```bash
cp "$PROD_DIR/.env" "$PROD_DIR/.env.backup.$(date +%Y%m%d%H%M%S)"
sudo cp /etc/nginx/sites-available/api.conf /etc/nginx/sites-available/api.conf.backup.$(date +%Y%m%d%H%M%S)
sudo cp /etc/nginx/sites-available/cms.yug-postel.ru.conf /etc/nginx/sites-available/cms.yug-postel.ru.conf.backup.$(date +%Y%m%d%H%M%S)
```

If those nginx paths differ on the VM, find the real ones first:

```bash
sudo nginx -T | rg -n "server_name api\\.yug-postel\\.ru|server_name cms\\.yug-postel\\.ru"
```

## Step 4: Check Out The Bootstrap Ref And Record The SHA

The CI/CD rewrite commit is classified as `destructive`, so the automatic production runtime workflow will refuse to deploy it. The first release must therefore be bootstrapped manually on the VM.

On the VM, run:

```bash
cd "$PROD_DIR"
git fetch origin <YOUR_REWRITE_BRANCH>
git checkout <YOUR_REWRITE_BRANCH>
git pull --ff-only origin <YOUR_REWRITE_BRANCH>
SHA="$(git rev-parse HEAD)"
echo "$SHA"
```

Required result:

- the checkout succeeds
- the working tree stays clean
- you have one exact commit SHA recorded in `SHA`

## Step 5: Log In To GHCR For The One-Time Bootstrap Image Push

Create a temporary GitHub token that can read and write `ghcr.io/<repo-owner>/eshop-api`.

Log in on the VM:

```bash
docker login ghcr.io -u <YOUR_GITHUB_USERNAME>
```

When prompted for the password, paste the temporary token.

## Step 6: Build And Push The Bootstrap API Image

Run:

```bash
cd "$PROD_DIR"
docker build -f Dockerfile -t ghcr.io/anarchistische-ecke/eshop-api:"$SHA" .
docker push ghcr.io/anarchistische-ecke/eshop-api:"$SHA"
```

Required result:

- the image is pushed successfully as `ghcr.io/anarchistische-ecke/eshop-api:$SHA`

## Step 7: Run The Manual Blue-Green Bootstrap

Run:

```bash
cd "$PROD_DIR"
bash ./scripts/deploy-runtime-bluegreen.sh \
  --env-file .env \
  --compose-file docker-compose.prod.yml \
  --deploy-ref <YOUR_REWRITE_BRANCH> \
  --deploy-sha "$SHA" \
  --api-image-repository ghcr.io/anarchistische-ecke/eshop-api \
  --api-image-tag "$SHA" \
  --run-id bootstrap-$(date +%Y%m%d%H%M%S)
```

Expected behavior:

- shared infrastructure stays on `docker-compose.prod.yml`
- one runtime slot starts on either blue or green loopback ports
- candidate internal health checks succeed
- nginx upstream include files switch from `8080/8055` to one runtime slot
- public post-cutover health checks succeed
- `.deploy-state/runtime-live.env` is created

## Step 8: Verify The Bootstrap Immediately

Run:

```bash
cd "$PROD_DIR"
cat .deploy-state/runtime-live.env
cat .deploy-state/latest-runtime-summary.txt
cat /etc/nginx/includes/eshop-api-upstream.conf
cat /etc/nginx/includes/eshop-cms-upstream.conf
curl -i https://api.yug-postel.ru/health/redis
curl -i https://cms.yug-postel.ru/server/health
curl -i 'https://api.yug-postel.ru/content/navigation?placement=header'
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
```

Required result:

- `.deploy-state/runtime-live.env` exists
- `.deploy-state/latest-runtime-summary.txt` reports success
- the upstream include files now point to either `18080/18055` or `28080/28055`
- public API health is `200`
- public CMS health is `200`
- public content health is `200`

## Step 9: Understand The First-Bootstrap Rollback Boundary

After the first successful bootstrap:

- the legacy `docker-compose.prod.yml` API and Directus services are stopped
- there is no recorded previous blue-green release yet
- `rollback-production.yml` is not useful until a later successful blue-green deploy creates a previous live release

Emergency fallback for the first bootstrap only:

```bash
printf 'proxy_pass http://127.0.0.1:8080;\n' | sudo tee /etc/nginx/includes/eshop-api-upstream.conf >/dev/null
printf 'proxy_pass http://127.0.0.1:8055;\n' | sudo tee /etc/nginx/includes/eshop-cms-upstream.conf >/dev/null
sudo nginx -t
sudo systemctl reload nginx
cd "$PROD_DIR"
docker compose --env-file .env -f docker-compose.prod.yml up -d api directus
```

Use that fallback only if the very first bootstrap must be undone before any later blue-green release exists.

## Step 10: Normal Operation After Bootstrap

After the bootstrap is verified:

1. Merge the rewrite branch to `main`.
2. Expect the merge commit itself to still be classified as `destructive`.
3. Do not force `deploy-production-runtime.yml` for that bootstrap commit. The server is already on it.
4. For later runtime-safe changes, merge to `main` and let `.github/workflows/deploy-production-runtime.yml` run automatically.
5. Use `.github/workflows/rollback-production.yml` only after at least one later successful runtime-safe deploy exists.
6. Leave `.github/workflows/deploy-production-destructive.yml` unused until you have a separate real staging target.
