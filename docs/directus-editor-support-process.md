# Directus Editor Support Process

This document defines the support path for CMS editors, publishers, and administrators.

## Goal

Give non-technical users one clear path for help, keep content issues out of ad-hoc chats, and route technical incidents to the right owner quickly.

## Intake Path

Adopt one shared support intake for all CMS requests:

- primary channel: `#cms-support` or the equivalent shared ticket queue
- urgent production incident path: on-call or incident channel used for storefront issues

Every request should include:

- Directus URL and environment: `local`, `staging`, or `production`
- collection and item involved
- exact action attempted
- screenshot or screen recording if possible
- time of issue
- user email

## Severity Levels

- `P1`: editors cannot log in, Directus is down, publishing is blocked for all users, or CMS changes break the storefront
- `P2`: one business-critical collection or media flow is failing, but a workaround exists
- `P3`: one editor has a permissions or content issue with limited scope
- `P4`: training question, minor usability issue, or low-priority content request

Recommended response targets:

- `P1`: acknowledge within `15 minutes`
- `P2`: acknowledge within `1 hour`
- `P3`: acknowledge within `4 business hours`
- `P4`: acknowledge within `1 business day`

## Ownership

- `CMS Editor`: raises content or access issues with enough context
- `CMS Publisher`: first-line content process support and publish/review questions
- `CMS Administrator`: Directus roles, permissions, SSO mapping, schema, and collection issues
- `Backend Owner`: `/content/*` delivery path, cache, preview endpoints, and API errors
- `Infrastructure Owner`: Directus container, Postgres, Redis, Keycloak, object storage, nginx, and VM-level incidents

## Standard Resolution Paths

### Login Or Permission Problems

1. Confirm the user exists in Keycloak.
2. Confirm the user has exactly one mapped realm role: `manager`, `publisher`, or `admin`.
3. Confirm the Directus SSO mapping still matches [directus-editor-onboarding.md](./directus-editor-onboarding.md).
4. If needed, rerun the Directus SSO/bootstrap process.

### Content Not Visible On The Storefront

1. Confirm the item `status` is `published`.
2. Confirm the publisher approved the latest revision.
3. Invalidate backend CMS cache if stale data is suspected.
4. Check backend `/content/*` responses and logs.

### Media Problems

1. Confirm the file exists in `File Library`.
2. Confirm the item references the correct Directus file.
3. Confirm asset delivery works through Directus `/assets` or the configured public URL.
4. Check S3-compatible storage connectivity if uploads fail.

### Directus Service Incident

1. Use [directus-operations-runbook.md](./directus-operations-runbook.md) for restart and health checks.
2. If the latest rollout caused the issue, follow [directus-rollback-strategy.md](./directus-rollback-strategy.md).
3. If content or schema corruption is involved, restore from the appropriate backup before reopening editor access.

## When To Escalate Immediately

Escalate without delay when:

- more than one editor is blocked from logging in
- published content disappears or is obviously stale after approval
- uploads fail across the board
- Directus or Keycloak health checks fail
- storefront navigation or legal pages become incorrect in production

## Editor-Facing Promise

Editors should not need to decide whether a problem is “backend”, “Directus”, or “infrastructure”. They should use the shared support intake and include evidence. Triage belongs to the CMS administrator and technical owners.

## Related References

- [directus-editor-guide.md](./directus-editor-guide.md)
- [directus-editor-onboarding.md](./directus-editor-onboarding.md)
- [directus-editorial-workflow.md](./directus-editorial-workflow.md)
- [directus-operations-runbook.md](./directus-operations-runbook.md)
- [directus-rollback-strategy.md](./directus-rollback-strategy.md)
