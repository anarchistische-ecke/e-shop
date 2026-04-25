# Production VM Preparation For Single-VM Blue-Green CI/CD

This is the authoritative runbook for preparing a production VM for the current CI/CD pipeline as it exists in this repo today.

Use this document only for the current operating model:

- one Debian 11 VM
- production only
- no real staging target yet
- GitHub-hosted Actions connect to the VM over SSH and run deploy scripts inside an existing checkout

Do not point the `staging` GitHub Environment or `deploy-staging-runtime.yml` at the production VM.

After this runbook is complete, continue with [single-vm-production-bringup.md](./single-vm-production-bringup.md) for the one-time first blue-green bootstrap.

## Current Pipeline Overview

| Workflow | Trigger | What it does now | Current limit |
| --- | --- | --- | --- |
| `.github/workflows/backend-ci.yml` | pull requests to `main`, manual dispatch | Validates change classification, Directus schema snapshot, Directus runtime extensions, and the Java build/test path | CI only. No deploy. |
| `.github/workflows/deploy-production-runtime.yml` | push to `main`, manual dispatch | Builds `ghcr.io/<repo-owner>/eshop-api:<sha>` and deploys automatically on the production VM | Runtime-safe changes use blue-green cutover. Destructive changes print a warning and run the in-place destructive apply path. |
| `.github/workflows/deploy-production-destructive.yml` | manual dispatch | Runs the destructive/manual deploy path with `scripts/deploy-stack.sh` | Manual in-place destructive apply with optional backup skip. Not blue-green. |
| `.github/workflows/rollback-production.yml` | manual dispatch | Runs `scripts/rollback-runtime-release.sh` on the production VM | Can only roll back to the recorded previous live blue-green release. |
| `.github/workflows/ops-health-check.yml` | every 15 minutes, manual dispatch | SSHes to the VM and runs `scripts/check-stack-health.sh` | `staging` should remain unused until a separate staging target exists. |

Related but not currently usable in production:

- `.github/workflows/deploy-staging-runtime.yml` exists, but it should stay unused until you have a separate staging target.

## Blue-Green Suitability On One VM

The current setup is suitable for blue-green deployment on a single VM only for runtime-safe application changes:

- API code changes
- Directus runtime-extension changes
- other non-destructive runtime changes that do not alter shared data or the shared infrastructure contract

The current setup is not true full-stack blue-green for these reasons:

- PostgreSQL and Redis are shared between the live slot and the candidate slot
- changes to `docker-compose.prod.yml`, `docker-compose.runtime-slot.yml`, Directus schema, Directus seeds, and `scripts/directus-*` are classified as destructive
- destructive changes are applied in place with `scripts/deploy-stack.sh`, not through slot-based blue-green cutover
- the first production bootstrap is manual because that initial CI/CD rewrite commit is itself destructive

Treat the production model as:

- blue-green for runtime-safe releases
- manual/special-case for first bootstrap
- automatic in-place apply for destructive releases

## VM Contract

Before the first deploy, the VM must satisfy all of the following:

- OS: Debian 11
- inbound connectivity: SSH on `22`, HTTP on `80`, HTTPS on `443`
- outbound connectivity:
  - `github.com` for `git fetch origin`
  - `ghcr.io` for Docker image pulls
  - the public API, CMS, and storefront hostnames used by `PUBLIC_API_HEALTHCHECK_URL`, `PUBLIC_CONTENT_HEALTHCHECK_URL`, and `PUBLIC_STOREFRONT_HEALTHCHECK_URL`
  - the Keycloak issuer URL configured in `DIRECTUS_AUTH_KEYCLOAK_ISSUER_URL`
- deploy user:
  - owns or can write the deploy checkout
  - can run `docker` and `docker compose` without `sudo`
  - can run `sudo -n nginx -t`
  - can run `sudo -n systemctl reload nginx`
- repository checkout:
  - exists on disk at one fixed deploy path
  - has an `origin` remote that supports non-interactive `git fetch origin`
  - stays clean enough for `git checkout` and `git pull --ff-only` during deploys
- nginx:
  - API vhost includes `/etc/nginx/includes/eshop-api-upstream.conf`
  - CMS vhost includes `/etc/nginx/includes/eshop-cms-upstream.conf`
  - storefront origin vhost includes `/etc/nginx/includes/eshop-storefront-upstream.conf`
- runtime layout:
  - `<deploy-path>/releases`
  - `<deploy-path>/.deploy-state`
  - `<deploy-path>/.deploy-state/logs`
  - `<deploy-path>/backups/directus`
- VM-local `.env`:
  - must exist at `<deploy-path>/.env`
  - must use plain `KEY=value` assignments only
  - must not contain `export KEY=value`
  - must not contain shell expressions, command substitution, or inline comments after values
  - must not leave placeholder values like `<fill-me>` because the env loader rejects angle-bracket placeholders

Important networking note:

- `docker-compose.prod.yml` publishes `5432`, `6379`, `8080`, and `8055` on the host, and binds the storefront origin only on `127.0.0.1:${STOREFRONT_HOST_PORT:-3000}`
- `docker-compose.runtime-slot.yml` binds blue/green candidate ports to `127.0.0.1`
- do not expose `5432`, `6379`, `8080`, `8055`, `3000`, `18080`, `18055`, `13000`, `28080`, `28055`, or `23000` publicly
- enforce that restriction at the cloud firewall or equivalent perimeter layer; do not assume Docker-published ports are hidden automatically

## Fresh Debian 11 VM Preparation

Use this path when the VM is new or when you are rebuilding production from a clean Debian 11 host.

### Step 1: Choose The Deploy User And Path

Use these values unless you already have an approved existing deploy path:

```bash
export DEPLOY_USER=eshop
export DEPLOY_GROUP=eshop
export DEPLOY_PATH=/srv/eshop
```

If you already run production from another path and want to preserve it, keep that existing path and use it consistently everywhere:

- in the VM filesystem
- in `DEPLOY_RUNTIME_RELEASES_DIR`
- in `DEPLOY_RUNTIME_STATE_DIR`
- in the GitHub secret `YC_VM_DEPLOY_PATH`

### Step 2: Install Required Debian Packages

Run as a privileged admin user on the VM:

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl git gnupg nginx openssh-server sudo util-linux
sudo systemctl enable --now ssh nginx
```

Required result:

- `git`, `curl`, `nginx`, `sudo`, and `flock` are installed

### Step 3: Install Docker Engine And Docker Compose Plugin

If any old Docker packages are present, remove them first:

```bash
sudo apt-get remove -y docker.io docker-compose docker-doc podman-docker containerd runc || true
```

Install Docker from Docker's official Debian repository:

```bash
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/debian/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/debian bullseye stable" | sudo tee /etc/apt/sources.list.d/docker.list >/dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
```

Validate:

```bash
docker version
docker compose version
sudo systemctl status docker --no-pager
```

Required result:

- Docker engine is running
- `docker compose version` succeeds

### Step 4: Create The Deploy User

Create the deploy user and the deploy directory:

```bash
sudo adduser --disabled-password --gecos "" "$DEPLOY_USER"
sudo install -d -o "$DEPLOY_USER" -g "$DEPLOY_GROUP" "$DEPLOY_PATH"
sudo usermod -aG docker "$DEPLOY_USER"
```

The deploy user must log out and back in once before the `docker` group membership is effective.

### Step 5: Allow Only The Required Non-Interactive Sudo Commands

Create `/etc/sudoers.d/eshop-deploy-nginx` with this exact content:

```sudoers
Cmnd_Alias ESHOP_NGINX = /usr/sbin/nginx -t, /bin/systemctl reload nginx, /usr/bin/systemctl reload nginx
eshop ALL=(root) NOPASSWD: ESHOP_NGINX
```

If your deploy user is not literally `eshop`, replace `eshop` in that file with the real deploy username.

Install it and validate it:

```bash
cat <<'EOF' | sudo tee /etc/sudoers.d/eshop-deploy-nginx >/dev/null
Cmnd_Alias ESHOP_NGINX = /usr/sbin/nginx -t, /bin/systemctl reload nginx, /usr/bin/systemctl reload nginx
eshop ALL=(root) NOPASSWD: ESHOP_NGINX
EOF
sudo chmod 440 /etc/sudoers.d/eshop-deploy-nginx
sudo visudo -cf /etc/sudoers.d/eshop-deploy-nginx
```

Later validation must succeed as the deploy user:

```bash
sudo -n nginx -t
sudo -n systemctl reload nginx
```

### Step 6: Install The GitHub Actions SSH Key On The VM

Generate one dedicated SSH keypair on a secure operator workstation, not on the VM:

```bash
ssh-keygen -t ed25519 -f ./yc-vm-production -C github-actions-production
```

This produces:

- private key: `./yc-vm-production`
- public key: `./yc-vm-production.pub`

Install the public key for the deploy user on the VM:

```bash
sudo install -d -m 700 -o "$DEPLOY_USER" -g "$DEPLOY_GROUP" "/home/$DEPLOY_USER/.ssh"
sudo tee "/home/$DEPLOY_USER/.ssh/authorized_keys" >/dev/null <<'EOF'
paste-the-public-key-from-yc-vm-production.pub-here
EOF
sudo chown "$DEPLOY_USER:$DEPLOY_GROUP" "/home/$DEPLOY_USER/.ssh/authorized_keys"
sudo chmod 600 "/home/$DEPLOY_USER/.ssh/authorized_keys"
```

Store the private key contents later as the GitHub secret `YC_VM_SSH_KEY`.

Recommended SSH hardening on the VM:

```bash
cat <<'EOF' | sudo tee /etc/ssh/sshd_config.d/99-eshop.conf >/dev/null
PasswordAuthentication no
KbdInteractiveAuthentication no
ChallengeResponseAuthentication no
PermitRootLogin no
PubkeyAuthentication yes
EOF
sudo /usr/sbin/sshd -t
sudo systemctl reload ssh
```

### Step 7: Create The Repository Checkout

The deploy scripts require a real git checkout with an `origin` remote. A tarball or copied source directory is not acceptable because deploys run:

- `git fetch origin`
- `git checkout`
- `git pull --ff-only`
- `git worktree add`

#### Option A: Public HTTPS Clone

This repo is currently reachable with a public HTTPS remote, so the simplest checkout is:

```bash
sudo -u "$DEPLOY_USER" git clone https://github.com/anarchistische-ecke/e-shop.git "$DEPLOY_PATH"
```

Validate as the deploy user:

```bash
sudo -u "$DEPLOY_USER" git -C "$DEPLOY_PATH" remote -v
sudo -u "$DEPLOY_USER" git -C "$DEPLOY_PATH" ls-remote origin HEAD
```

#### Option B: Private Repo With A Read-Only Deploy Key

Use this if the repository is private or if public HTTPS clone is no longer allowed.

Generate a deploy key on the VM as the deploy user:

```bash
sudo -u "$DEPLOY_USER" ssh-keygen -t ed25519 -f "/home/$DEPLOY_USER/.ssh/github_eshop_deploy" -N "" -C github-eshop-deploy
sudo -u "$DEPLOY_USER" cat "/home/$DEPLOY_USER/.ssh/github_eshop_deploy.pub"
```

Add that public key in GitHub as a read-only deploy key for this repository.

Create `/home/$DEPLOY_USER/.ssh/config`:

```bash
cat <<'EOF' | sudo tee "/home/$DEPLOY_USER/.ssh/config" >/dev/null
Host github.com-eshop
    HostName github.com
    User git
    IdentityFile /home/eshop/.ssh/github_eshop_deploy
    IdentitiesOnly yes
EOF
sudo chown "$DEPLOY_USER:$DEPLOY_GROUP" "/home/$DEPLOY_USER/.ssh/config"
```

Literal file content:

```sshconfig
Host github.com-eshop
    HostName github.com
    User git
    IdentityFile /home/eshop/.ssh/github_eshop_deploy
    IdentitiesOnly yes
```

If your deploy user is not literally `eshop`, replace `/home/eshop/` in that file with the real home directory for the deploy user.

Populate GitHub's host key:

```bash
sudo -u "$DEPLOY_USER" sh -c 'ssh-keyscan github.com >> ~/.ssh/known_hosts'
sudo chown "$DEPLOY_USER:$DEPLOY_GROUP" "/home/$DEPLOY_USER/.ssh/known_hosts"
sudo chmod 600 "/home/$DEPLOY_USER/.ssh/config" "/home/$DEPLOY_USER/.ssh/known_hosts"
```

Clone using the SSH host alias:

```bash
sudo -u "$DEPLOY_USER" git clone git@github.com-eshop:anarchistische-ecke/e-shop.git "$DEPLOY_PATH"
```

Validate:

```bash
sudo -u "$DEPLOY_USER" git -C "$DEPLOY_PATH" ls-remote origin HEAD
```

#### Option C: Private Repo With HTTPS And A PAT

Use this only if you intentionally want HTTPS credentials stored on the VM.

Create a GitHub token with repository read access, then configure Git credentials as the deploy user:

```bash
sudo -u "$DEPLOY_USER" git config --global credential.helper store
sudo -u "$DEPLOY_USER" sh -c 'cat > ~/.git-credentials <<'"'"'EOF'"'"'
https://github-username:github-token@github.com
EOF'
sudo chmod 600 "/home/$DEPLOY_USER/.git-credentials"
sudo -u "$DEPLOY_USER" git clone https://github.com/anarchistische-ecke/e-shop.git "$DEPLOY_PATH"
```

Validate:

```bash
sudo -u "$DEPLOY_USER" git -C "$DEPLOY_PATH" ls-remote origin HEAD
```

### Step 8: Create The Runtime Directories

Run as the deploy user:

```bash
sudo -u "$DEPLOY_USER" mkdir -p "$DEPLOY_PATH/releases"
sudo -u "$DEPLOY_USER" mkdir -p "$DEPLOY_PATH/.deploy-state/logs"
sudo -u "$DEPLOY_USER" mkdir -p "$DEPLOY_PATH/backups/directus"
```

Validate:

```bash
sudo -u "$DEPLOY_USER" ls -ld "$DEPLOY_PATH/releases" "$DEPLOY_PATH/.deploy-state" "$DEPLOY_PATH/.deploy-state/logs" "$DEPLOY_PATH/backups/directus"
```

### Step 9: Create The Nginx Upstream Include Files

Create the include directory and initial files:

```bash
sudo mkdir -p /etc/nginx/includes
printf 'proxy_pass http://127.0.0.1:8080;\n' | sudo tee /etc/nginx/includes/eshop-api-upstream.conf >/dev/null
printf 'proxy_pass http://127.0.0.1:8055;\n' | sudo tee /etc/nginx/includes/eshop-cms-upstream.conf >/dev/null
printf 'proxy_pass http://127.0.0.1:3000;\n' | sudo tee /etc/nginx/includes/eshop-storefront-upstream.conf >/dev/null
```

Required contents before the first bootstrap:

```nginx
proxy_pass http://127.0.0.1:8080;
proxy_pass http://127.0.0.1:8055;
proxy_pass http://127.0.0.1:3000;
```

### Step 10: Update The API, CMS, And Storefront Nginx Vhosts

The HTTPS API server block must include:

```nginx
location / {
    include /etc/nginx/includes/eshop-api-upstream.conf;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto https;
    proxy_set_header X-Forwarded-Host $host;
    proxy_set_header X-Forwarded-Port 443;
}
```

The HTTPS CMS server block must include:

```nginx
location / {
    include /etc/nginx/includes/eshop-cms-upstream.conf;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto https;
    proxy_set_header X-Forwarded-Host $host;
    proxy_set_header X-Forwarded-Port 443;
}
```

Repo examples:

- [ops/nginx/api.yug-postel.ru.conf.example](./../ops/nginx/api.yug-postel.ru.conf.example)
- [ops/nginx/cms.yug-postel.ru.conf.example](./../ops/nginx/cms.yug-postel.ru.conf.example)
- [ops/nginx/yug-postel.ru.conf.example](./../ops/nginx/yug-postel.ru.conf.example)

Validate nginx:

```bash
sudo nginx -t
sudo systemctl reload nginx
```

### Step 11: Create The Production `.env`

Create `<deploy-path>/.env` with real values. Use plain `KEY=value` lines only.

This template is safe to paste because it uses `replace-me` style placeholders instead of angle brackets:

```env
SPRING_PROFILES_ACTIVE=prod

SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/eshop
SPRING_DATASOURCE_USERNAME=eshop_app
SPRING_DATASOURCE_PASSWORD=replace-me-eshop-db-password
ESHOP_DB_DATABASE=eshop
ESHOP_DB_USER=eshop_app
ESHOP_DB_PASSWORD=replace-me-eshop-db-password

POSTGRES_DB=eshop
POSTGRES_USER=postgres_admin
POSTGRES_PASSWORD=replace-me-postgres-admin-password

SPRING_DATA_REDIS_HOST=redis
SPRING_DATA_REDIS_PORT=6379
DIRECTUS_REDIS_URL=redis://redis:6379

CORS_ALLOWED_ORIGINS=https://yug-postel.ru
JWT_SECRET=replace-me-long-random-jwt-secret

YANDEX_STORAGE_BUCKET=replace-me-commerce-bucket
YANDEX_STORAGE_KEY=replace-me-commerce-storage-key
YANDEX_STORAGE_SECRET=replace-me-commerce-storage-secret
YANDEX_STORAGE_ENDPOINT=https://storage.yandexcloud.net

KEYCLOAK_ISSUER_URI=https://auth.yug-postel.ru/realms/yug-postel

DIRECTUS_VERSION=11.17.2
DIRECTUS_KEY=replace-me-long-random-directus-key
DIRECTUS_SECRET=replace-me-long-random-directus-secret
DIRECTUS_ADMIN_EMAIL=replace-me-directus-admin-email
DIRECTUS_ADMIN_PASSWORD=replace-me-directus-admin-password
DIRECTUS_BASE_URL=http://directus:8055
DIRECTUS_PUBLIC_URL=https://cms.yug-postel.ru
DIRECTUS_IP_TRUST_PROXY=true
DIRECTUS_SCHEMA_ADMIN_TOKEN=replace-me-directus-schema-token
DIRECTUS_CMS_CONTENT_COLLECTIONS=site_settings,navigation,navigation_items,page,page_sections,page_section_items,faq,legal_documents,banner,post,product_overlay,category_overlay,catalogue_overlay_block,catalogue_overlay_block_item,storefront_collection,storefront_collection_item

DIRECTUS_DB_DATABASE=directus
DIRECTUS_DB_USER=directus
DIRECTUS_DB_PASSWORD=replace-me-directus-db-password

DIRECTUS_AUTH_PROVIDERS=keycloak
DIRECTUS_AUTH_DISABLE_DEFAULT=true
DIRECTUS_AUTH_KEYCLOAK_CLIENT_ID=directus
DIRECTUS_AUTH_KEYCLOAK_CLIENT_SECRET=replace-me-directus-keycloak-client-secret
DIRECTUS_AUTH_KEYCLOAK_ISSUER_URL=https://auth.yug-postel.ru/realms/yug-postel/.well-known/openid-configuration
DIRECTUS_AUTH_KEYCLOAK_ISSUER_DISCOVERY_MUST_SUCCEED=false

DIRECTUS_BRIDGE_TOKEN=replace-me-directus-bridge-token
DIRECTUS_STOREFRONT_OPS_BACKEND_URL=http://api:8080

DIRECTUS_STORAGE_S3_KEY=replace-me-directus-storage-key
DIRECTUS_STORAGE_S3_SECRET=replace-me-directus-storage-secret
DIRECTUS_STORAGE_S3_BUCKET=replace-me-directus-assets-bucket
DIRECTUS_STORAGE_S3_REGION=ru-central1
DIRECTUS_STORAGE_S3_ENDPOINT=https://storage.yandexcloud.net
DIRECTUS_STORAGE_S3_FORCE_PATH_STYLE=false

DIRECTUS_CACHE_TTL=PT5M
DIRECTUS_CACHE_STALE_TTL=PT1H
DIRECTUS_RESPONSE_CACHE_MAX_AGE=PT1M
DIRECTUS_RESPONSE_CACHE_STALE_WHILE_REVALIDATE=PT5M
DIRECTUS_RESPONSE_CACHE_STALE_IF_ERROR=PT1H
DIRECTUS_DATA_CACHE_ENABLED=true
DIRECTUS_DATA_CACHE_TTL=5m
DIRECTUS_DATA_CACHE_AUTO_PURGE=true
DIRECTUS_DATA_CACHE_STORE=redis
DIRECTUS_DATA_CACHE_STATUS_HEADER=X-Directus-Cache

PUBLIC_API_HEALTHCHECK_URL=https://api.yug-postel.ru/health/redis
PUBLIC_DIRECTUS_HEALTHCHECK_URL=https://cms.yug-postel.ru/server/health
PUBLIC_CONTENT_HEALTHCHECK_URL=https://api.yug-postel.ru/content/navigation?placement=header
PUBLIC_STOREFRONT_HEALTHCHECK_URL=https://yug-postel.ru/healthz
STOREFRONT_PUBLIC_URL=https://yug-postel.ru
STOREFRONT_HOST_PORT=3000
STOREFRONT_SERVER_API_BASE=http://api:8080
STOREFRONT_IMAGE_REPOSITORY=ghcr.io/anarchistische-ecke/cozyhome-storefront
STOREFRONT_IMAGE_TAG=main
REACT_APP_SITE_URL=https://yug-postel.ru
REACT_APP_API_BASE=https://api.yug-postel.ru
REACT_APP_KEYCLOAK_URL=https://yug-postel.ru/auth
REACT_APP_KEYCLOAK_REALM=cozyhome
REACT_APP_KEYCLOAK_CLIENT_ID=cozyhome-web

DEPLOY_RUNTIME_RELEASES_DIR=/srv/eshop/releases
DEPLOY_RUNTIME_STATE_DIR=/srv/eshop/.deploy-state
DEPLOY_RUNTIME_BLUE_API_PORT=18080
DEPLOY_RUNTIME_BLUE_DIRECTUS_PORT=18055
DEPLOY_RUNTIME_BLUE_STOREFRONT_PORT=13000
DEPLOY_RUNTIME_GREEN_API_PORT=28080
DEPLOY_RUNTIME_GREEN_DIRECTUS_PORT=28055
DEPLOY_RUNTIME_GREEN_STOREFRONT_PORT=23000
DEPLOY_SHARED_DOCKER_NETWORK=eshop-shared
DEPLOY_NGINX_API_UPSTREAM_INCLUDE=/etc/nginx/includes/eshop-api-upstream.conf
DEPLOY_NGINX_CMS_UPSTREAM_INCLUDE=/etc/nginx/includes/eshop-cms-upstream.conf
DEPLOY_NGINX_STOREFRONT_UPSTREAM_INCLUDE=/etc/nginx/includes/eshop-storefront-upstream.conf
DEPLOY_RUNTIME_MIN_AVAILABLE_MEMORY_MB=1024
DEPLOY_RUNTIME_MIN_AVAILABLE_DISK_MB=1024
DEPLOY_RUNTIME_OBSERVATION_SECONDS=15
```

If your deploy path is not `/srv/eshop`, change only these path values to the real deploy path:

- `DEPLOY_RUNTIME_RELEASES_DIR`
- `DEPLOY_RUNTIME_STATE_DIR`

Important `.env` requirements:

- `DIRECTUS_BASE_URL=http://directus:8055` is required for the production API profile
- `DIRECTUS_SCHEMA_ADMIN_TOKEN` is required in practice when `DIRECTUS_AUTH_DISABLE_DEFAULT=true`
- `DIRECTUS_CMS_CONTENT_COLLECTIONS` must be present if you want `scripts/directus-published-at-bootstrap.sh` to run successfully during bootstrap or destructive maintenance
- `DIRECTUS_AUTH_KEYCLOAK_ISSUER_URL` must be reachable from the VM
- `PUBLIC_API_HEALTHCHECK_URL`, `PUBLIC_DIRECTUS_HEALTHCHECK_URL`, `PUBLIC_CONTENT_HEALTHCHECK_URL`, and `PUBLIC_STOREFRONT_HEALTHCHECK_URL` must resolve from the VM itself
- `STOREFRONT_IMAGE_REPOSITORY` and `STOREFRONT_IMAGE_TAG` must point at a published storefront container image
- do not set `API_IMAGE_REPOSITORY` or `API_IMAGE_TAG` for normal GitHub-driven runtime deploys; workflows inject them

Optional values you may also set if you use them operationally:

- `API_HEALTHCHECK_URL`
- `DIRECTUS_HEALTHCHECK_URL`
- `STOREFRONT_HEALTHCHECK_URL`
- `CONTENT_HEALTHCHECK_URL`
- `APP_OBSERVABILITY_PROMETHEUS_TOKEN`
- `DIRECTUS_AUTH_KEYCLOAK_ROLE_MAPPING`
- `DIRECTUS_STOREFRONT_OPS_CATALOGUE_ROLE_IDS`
- `DIRECTUS_STOREFRONT_OPS_INVENTORY_ROLE_IDS`

## Existing Production VM Adaptation

Use this path when the VM already runs the application and you want to convert that host to the current blue-green contract without rebuilding the OS.

### Step 1: Capture The Existing Deploy Path

SSH to the VM and record the current checkout path:

```bash
cd ~/eshop
export PROD_DIR="$(pwd)"
echo "$PROD_DIR"
```

Use that exact path for:

- `YC_VM_DEPLOY_PATH`
- `DEPLOY_RUNTIME_RELEASES_DIR`
- `DEPLOY_RUNTIME_STATE_DIR`

### Step 2: Back Up The Current VM Configuration

Run:

```bash
cp "$PROD_DIR/.env" "$PROD_DIR/.env.backup.$(date +%Y%m%d%H%M%S)"
sudo cp /etc/nginx/sites-available/api.conf /etc/nginx/sites-available/api.conf.backup.$(date +%Y%m%d%H%M%S)
sudo cp /etc/nginx/sites-available/cms.yug-postel.ru.conf /etc/nginx/sites-available/cms.yug-postel.ru.conf.backup.$(date +%Y%m%d%H%M%S)
```

If those vhost paths differ on the VM, find the real paths first:

```bash
sudo nginx -T | rg -n "server_name api\\.yug-postel\\.ru|server_name cms\\.yug-postel\\.ru"
```

### Step 3: Confirm The Existing Runtime Baseline

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

### Step 4: Confirm The Shared Docker Network Contract

Run:

```bash
docker inspect eshop-api-1 --format '{{range $k, $_ := .NetworkSettings.Networks}}{{println $k}}{{end}}'
docker inspect eshop-directus-1 --format '{{range $k, $_ := .NetworkSettings.Networks}}{{println $k}}{{end}}'
```

Required result:

- both containers include a network literally named `eshop-shared`

### Step 5: Create The Runtime Directories And Nginx Include Files

Run:

```bash
mkdir -p "$PROD_DIR/releases"
mkdir -p "$PROD_DIR/.deploy-state/logs"
mkdir -p "$PROD_DIR/backups/directus"
sudo mkdir -p /etc/nginx/includes
printf 'proxy_pass http://127.0.0.1:8080;\n' | sudo tee /etc/nginx/includes/eshop-api-upstream.conf >/dev/null
printf 'proxy_pass http://127.0.0.1:8055;\n' | sudo tee /etc/nginx/includes/eshop-cms-upstream.conf >/dev/null
```

### Step 6: Convert The Nginx Vhosts To Include-Based Cutover

Update the API vhost so `location /` includes:

```nginx
include /etc/nginx/includes/eshop-api-upstream.conf;
```

Update the CMS vhost so `location /` includes:

```nginx
include /etc/nginx/includes/eshop-cms-upstream.conf;
```

Then validate:

```bash
sudo nginx -t
sudo systemctl reload nginx
curl -i https://api.yug-postel.ru/health/redis
curl -i https://cms.yug-postel.ru/server/health
```

### Step 7: Add The Runtime Variables To The Existing `.env`

At minimum, the existing production `.env` must now contain these blue-green keys with real values:

```env
DIRECTUS_PUBLIC_URL=https://cms.yug-postel.ru
DIRECTUS_BASE_URL=http://directus:8055
DIRECTUS_IP_TRUST_PROXY=true
PUBLIC_API_HEALTHCHECK_URL=https://api.yug-postel.ru/health/redis
PUBLIC_DIRECTUS_HEALTHCHECK_URL=https://cms.yug-postel.ru/server/health
PUBLIC_CONTENT_HEALTHCHECK_URL=https://api.yug-postel.ru/content/navigation?placement=header
PUBLIC_STOREFRONT_HEALTHCHECK_URL=https://yug-postel.ru/healthz
STOREFRONT_PUBLIC_URL=https://yug-postel.ru
STOREFRONT_HOST_PORT=3000
STOREFRONT_SERVER_API_BASE=http://api:8080
STOREFRONT_IMAGE_REPOSITORY=ghcr.io/anarchistische-ecke/cozyhome-storefront
STOREFRONT_IMAGE_TAG=main
REACT_APP_SITE_URL=https://yug-postel.ru
REACT_APP_API_BASE=https://api.yug-postel.ru
REACT_APP_KEYCLOAK_URL=https://yug-postel.ru/auth
REACT_APP_KEYCLOAK_REALM=cozyhome
REACT_APP_KEYCLOAK_CLIENT_ID=cozyhome-web
DIRECTUS_CMS_CONTENT_COLLECTIONS=site_settings,navigation,navigation_items,page,page_sections,page_section_items,faq,legal_documents,banner,post,product_overlay,category_overlay,catalogue_overlay_block,catalogue_overlay_block_item,storefront_collection,storefront_collection_item
DEPLOY_RUNTIME_RELEASES_DIR=/home/dingus/eshop/releases
DEPLOY_RUNTIME_STATE_DIR=/home/dingus/eshop/.deploy-state
DEPLOY_RUNTIME_BLUE_API_PORT=18080
DEPLOY_RUNTIME_BLUE_DIRECTUS_PORT=18055
DEPLOY_RUNTIME_BLUE_STOREFRONT_PORT=13000
DEPLOY_RUNTIME_GREEN_API_PORT=28080
DEPLOY_RUNTIME_GREEN_DIRECTUS_PORT=28055
DEPLOY_RUNTIME_GREEN_STOREFRONT_PORT=23000
DEPLOY_SHARED_DOCKER_NETWORK=eshop-shared
DEPLOY_NGINX_API_UPSTREAM_INCLUDE=/etc/nginx/includes/eshop-api-upstream.conf
DEPLOY_NGINX_CMS_UPSTREAM_INCLUDE=/etc/nginx/includes/eshop-cms-upstream.conf
DEPLOY_NGINX_STOREFRONT_UPSTREAM_INCLUDE=/etc/nginx/includes/eshop-storefront-upstream.conf
DEPLOY_RUNTIME_MIN_AVAILABLE_MEMORY_MB=1024
DEPLOY_RUNTIME_MIN_AVAILABLE_DISK_MB=1024
DEPLOY_RUNTIME_OBSERVATION_SECONDS=15
```

If your existing production deploy path is not `/home/dingus/eshop`, replace that path in those copied examples with the exact value of `PROD_DIR` from Step 1 of this section.

Also ensure these existing values are present and correct:

- `DIRECTUS_AUTH_KEYCLOAK_ISSUER_URL`
- `DIRECTUS_STOREFRONT_OPS_BACKEND_URL=http://api:8080`
- `DIRECTUS_DB_DATABASE`
- `DIRECTUS_DB_USER`
- `DIRECTUS_DB_PASSWORD`
- `DIRECTUS_STORAGE_S3_KEY`
- `DIRECTUS_STORAGE_S3_SECRET`
- `DIRECTUS_STORAGE_S3_BUCKET`
- `DIRECTUS_STORAGE_S3_REGION`
- `DIRECTUS_STORAGE_S3_ENDPOINT`
- `DIRECTUS_BRIDGE_TOKEN`
- `DIRECTUS_SCHEMA_ADMIN_TOKEN`

If `DIRECTUS_AUTH_KEYCLOAK_ISSUER_DISCOVERY_MUST_SUCCEED` is present, set it to `false`.

## GitHub Environment Configuration

Configure only these GitHub Environments now:

- `production`
- `production-destructive`

Do not configure `staging` to point at the production VM.

Set these secrets in both `production` and `production-destructive`:

- `YC_VM_HOST`
- `YC_VM_USER`
- `YC_VM_SSH_KEY`
- `YC_VM_DEPLOY_PATH`
- `GHCR_PULL_USERNAME`
- `GHCR_PULL_TOKEN`

Exact meaning:

- `YC_VM_HOST`: DNS name or IP that GitHub Actions uses for SSH
- `YC_VM_USER`: deploy user on the VM
- `YC_VM_SSH_KEY`: private key contents matching the public key in the deploy user's `authorized_keys`
- `YC_VM_DEPLOY_PATH`: exact checkout path on the VM
- `GHCR_PULL_USERNAME`: GitHub username or machine identity allowed to pull `ghcr.io/<owner>/eshop-api`
- `GHCR_PULL_TOKEN`: GitHub token with package pull access for that account

Policy:

- `production` should stay approval-free so pushes to `main` remain automatic for both runtime-safe and destructive deploys
- `production-destructive` is optional and can remain approval-gated if you want a separate manual emergency path

Current limitation:

- `deploy-staging-runtime.yml` still exists but should stay unused until a separate staging target exists
- destructive deploys are automatic, but they are not blue-green and should be treated as in-place applies against shared infrastructure

## Validation Checklist Before First Bootstrap

Run these checks as the deploy user on the VM after preparation is complete:

```bash
export DEPLOY_PATH=/srv/eshop
# If your real deploy path is different, replace /srv/eshop before continuing.
cd "$DEPLOY_PATH"
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
test -f /etc/nginx/includes/eshop-storefront-upstream.conf
ls -ld releases .deploy-state .deploy-state/logs backups/directus
grep -E '^(DIRECTUS_PUBLIC_URL|PUBLIC_API_HEALTHCHECK_URL|PUBLIC_DIRECTUS_HEALTHCHECK_URL|PUBLIC_CONTENT_HEALTHCHECK_URL|PUBLIC_STOREFRONT_HEALTHCHECK_URL|STOREFRONT_PUBLIC_URL|STOREFRONT_HOST_PORT|STOREFRONT_SERVER_API_BASE|STOREFRONT_IMAGE_REPOSITORY|STOREFRONT_IMAGE_TAG|REACT_APP_SITE_URL|REACT_APP_API_BASE|REACT_APP_KEYCLOAK_URL|REACT_APP_KEYCLOAK_REALM|REACT_APP_KEYCLOAK_CLIENT_ID|DEPLOY_RUNTIME_RELEASES_DIR|DEPLOY_RUNTIME_STATE_DIR|DEPLOY_RUNTIME_BLUE_API_PORT|DEPLOY_RUNTIME_BLUE_DIRECTUS_PORT|DEPLOY_RUNTIME_BLUE_STOREFRONT_PORT|DEPLOY_RUNTIME_GREEN_API_PORT|DEPLOY_RUNTIME_GREEN_DIRECTUS_PORT|DEPLOY_RUNTIME_GREEN_STOREFRONT_PORT|DEPLOY_SHARED_DOCKER_NETWORK|DEPLOY_NGINX_API_UPSTREAM_INCLUDE|DEPLOY_NGINX_CMS_UPSTREAM_INCLUDE|DEPLOY_NGINX_STOREFRONT_UPSTREAM_INCLUDE|DEPLOY_RUNTIME_MIN_AVAILABLE_MEMORY_MB|DEPLOY_RUNTIME_MIN_AVAILABLE_DISK_MB|DEPLOY_RUNTIME_OBSERVATION_SECONDS)=' .env
git status --short
```

Everything must pass. In particular:

- `sudo -n nginx -t` must not prompt for a password
- `git ls-remote origin HEAD` must succeed without interactive auth
- the `.env` file must exist and contain the blue-green keys
- `git status --short` should be empty before deploys

If `git status --short` shows modified tracked files, do not proceed with GitHub-driven deploys. The current SSH deploy flow assumes a clean checkout for `git checkout` and `git pull --ff-only`.

If the VM is an existing production host, also re-run:

```bash
curl -i https://api.yug-postel.ru/health/redis
curl -i https://cms.yug-postel.ru/server/health
curl -i 'https://api.yug-postel.ru/content/navigation?placement=header'
curl -i https://yug-postel.ru/healthz
curl -i https://yug-postel.ru/robots.txt
```

## Next Step

Once the VM, nginx, `.env`, GitHub Environments, and SSH contract are ready, continue with [single-vm-production-bringup.md](./single-vm-production-bringup.md).

That runbook covers only:

- the one-time first blue-green bootstrap
- immediate post-bootstrap verification
- the first-bootstrap rollback boundary
- normal operation after bootstrap
