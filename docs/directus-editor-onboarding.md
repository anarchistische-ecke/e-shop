# Directus Editor Onboarding

This document defines the supported process for adding a new CMS editor, publisher, or administrator through Keycloak and letting Directus provision the user through SSO.

The current SSO bootstrap and governance rules that this process depends on live in:

- [directus-content-governance.md](./directus-content-governance.md)
- [directus-environment.md](./directus-environment.md)
- [directus/docker-compose.yml](/Users/freddycooper/Documents/eshop/directus/docker-compose.yml)
- [directus-sso-bootstrap.sh](/Users/freddycooper/Documents/eshop/scripts/directus-sso-bootstrap.sh)

## Role Mapping

Keycloak realm roles map to Directus roles through `AUTH_KEYCLOAK_ROLE_MAPPING`.

| Keycloak realm role | Directus role | Intended user |
| --- | --- | --- |
| `manager` | `CMS Editor` | Draft author / content editor |
| `publisher` | `CMS Publisher` | Reviewer / approver |
| `admin` | `CMS Administrator` | Platform owner / CMS admin |

Important:

- Assign exactly one of the mapped realm roles above to a Directus user.
- Directus assigns only one Directus role per user from this mapping.
- Do not give the same person multiple mapped roles such as `manager` and `admin`.

## What Editors Can Do

The bootstrap script grants `CMS Editor` access only to the CMS collection allowlist from `DIRECTUS_CMS_CONTENT_COLLECTIONS` plus file/folder management:

- `site_settings`
- `navigation`
- `navigation_items`
- `page`
- `page_sections`
- `page_section_items`
- `faq`
- `legal_documents`
- `banner`
- `post`
- `directus_files`
- `directus_folders`

The editor role can:

- sign in to Directus Studio through Keycloak
- read, create, and update the allowlisted CMS collections
- upload and update CMS assets
- save content only as `draft`

The editor role cannot:

- publish or archive content
- delete content or files by default
- edit schema, roles, policies, users, or Directus settings
- access commerce collections, because they are not in the CMS allowlist

`CMS Publisher` adds publication rights for the same CMS collection allowlist. `CMS Administrator` has full Directus admin access.

## Local UI Process

Use this when you want to add a user manually in Keycloak Admin Console.

1. Open [http://localhost:8081/admin/master/console/](http://localhost:8081/admin/master/console/).
2. Sign in with the bootstrap admin from `keycloak/.env`.
3. Switch to realm `cozyhome`.
4. Create a new user with:
   - `username = email`
   - `email` set
   - `emailVerified = true`
   - `enabled = true`
5. Set a password under `Credentials`.
6. Under `Role mapping`, grant exactly one realm role:
   - `manager` for a normal editor
   - `publisher` for a reviewer/publisher
   - `admin` for a Directus administrator
7. Remove any other mapped realm roles from that user if they were assigned previously.
8. Have the user open [http://localhost:8055](http://localhost:8055) and click the `Keycloak` login button.
9. On first login, Directus provisions the user automatically and assigns the mapped Directus role.

## Local CLI Process

Use the helper script when you want a repeatable local onboarding flow without touching the Keycloak UI.

Example editor:

```bash
cd /Users/freddycooper/Documents/eshop
./scripts/keycloak-upsert-cms-user.sh \
  --email editor@example.com \
  --password 'Editor123!' \
  --role manager \
  --first-name CMS \
  --last-name Editor
```

Example publisher:

```bash
cd /Users/freddycooper/Documents/eshop
./scripts/keycloak-upsert-cms-user.sh \
  --email publisher@example.com \
  --password 'Publisher123!' \
  --role publisher \
  --first-name CMS \
  --last-name Publisher
```

Example admin with a temporary password:

```bash
cd /Users/freddycooper/Documents/eshop
./scripts/keycloak-upsert-cms-user.sh \
  --email cms-admin@example.com \
  --password 'TempAdmin123!' \
  --role admin \
  --temporary-password
```

The helper script:

- authenticates to Keycloak with the bootstrap admin from `keycloak/.env`
- creates or updates the user in realm `cozyhome`
- verifies the email flag is enabled
- resets the password
- removes any other mapped Directus realm roles
- assigns the selected realm role

## Verification Checklist

After onboarding a user, verify:

1. Keycloak user exists in realm `cozyhome` with exactly one mapped realm role among `manager`, `publisher`, `admin`.
2. The user can sign in to Directus through the `Keycloak` button.
3. The Directus user record is created with provider `keycloak`.
4. The user receives the expected Directus role:
   - `manager` -> `CMS Editor`
   - `publisher` -> `CMS Publisher`
   - `admin` -> `CMS Administrator`
5. A `CMS Editor` can edit CMS collections in the allowlist but cannot publish, archive, manage schema, or change security settings.

For permission verification, the current bootstrap source of truth is [directus-sso-bootstrap.sh](/Users/freddycooper/Documents/eshop/scripts/directus-sso-bootstrap.sh). It seeds:

- role-policy junctions for `CMS Editor`, `CMS Publisher`, and `CMS Administrator`
- file/folder permissions
- collection permissions only for `DIRECTUS_CMS_CONTENT_COLLECTIONS`
- public read filters only for `DIRECTUS_CMS_PUBLIC_COLLECTIONS`

## Production Process

Production onboarding should follow the same identity model:

1. Create the user in the production Keycloak realm.
2. Mark the email verified if your policy requires it.
3. Grant exactly one mapped realm role: `manager`, `publisher`, or `admin`.
4. Ensure production Directus has the matching `AUTH_KEYCLOAK_ROLE_MAPPING` and seeded Directus roles/policies.
5. Have the user sign in through the production Directus Keycloak login.

Adding a user does not require a new deployment if:

- the Keycloak client already exists
- the mapped realm roles already exist
- Directus roles/policies were already seeded

## Troubleshooting

If the user can log in to Keycloak but lands in Directus without the expected rights:

- check that the user has only one mapped realm role
- check `AUTH_KEYCLOAK_ROLE_MAPPING` in the Directus runtime env
- rerun [directus-sso-bootstrap.sh](/Users/freddycooper/Documents/eshop/scripts/directus-sso-bootstrap.sh) if roles or permissions drifted

If the user cannot log in to Directus at all:

- verify the `directus` Keycloak client exists
- verify the redirect URI includes `http://localhost:8055/auth/login/keycloak/callback` for local dev
- verify `DIRECTUS_AUTH_KEYCLOAK_ISSUER_URL` still points at the reachable realm issuer
