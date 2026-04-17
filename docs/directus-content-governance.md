# Directus Content Governance

This document defines the least-privilege Directus access model for CMS-managed content in the current Cozyhome rollout.

The approved collection architecture that these permissions target is documented in [directus-content-model.md](./directus-content-model.md).

## Collection Scope

The current governance bootstrap targets the approved phase-1 collection ids:

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
- `product_overlay`
- `category_overlay`
- `catalogue_overlay_block`
- `catalogue_overlay_block_item`
- `storefront_collection`
- `storefront_collection_item`

`post` is optional for phase 1, but the permission baseline allows for it so it can be added without redefining roles.

## Workflow Convention

Every CMS collection intended for editorial workflow and public delivery must include a `status` field. The baseline workflow states are:

- `draft`
- `in_review`
- `published`
- `archived`

This is the contract used by the public-read filter and by the editor/publisher validation rules in the bootstrap.

Every governed public collection must also carry `published_at`. The governance bootstrap stamps it automatically when a record enters `published`, clears it when a record leaves `published`, and the CMS content audit treats any published record without it as invalid.

Expected progression:

- Editors create and revise content in `draft`.
- Editors submit content for approval by moving it to `in_review`.
- Publishers approve content by moving it from `in_review` to `published`.
- Publishers can return content from `in_review` to `draft` when revisions are required.
- Archived content stays out of both public reads and preview reads.

## Roles And Policies

### CMS Editor

- Directus role: `CMS Editor`
- Directus policy: `CMS Editor Policy`
- Intended users: content authors and merchandisers preparing draft content
- Studio access: yes
- Admin/settings access: no

Allowed actions:

- Read, create, and update CMS-managed collections in the allowlist
- Read, create, and update `directus_files`
- Read, create, and update `directus_folders`

Not allowed:

- Publish content
- Archive content
- Delete content or assets
- Change access-control settings, users, roles, policies, or schema

Status rules:

- New items default to `draft`
- Create validation only allows `draft`
- Update validation allows `draft` and `in_review`
- Editors can submit content for approval, but they cannot publish or archive it

### CMS Publisher

- Directus role: `CMS Publisher`
- Directus policy: `CMS Publisher Policy`
- Intended users: reviewers, approvers, senior content operators
- Studio access: yes
- Admin/settings access: no

Allowed actions:

- Read, create, and update CMS-managed collections in the allowlist
- Read, create, and update `directus_files`
- Read, create, and update `directus_folders`
- Move content through review and publication states

Not allowed:

- Change schema or global security configuration
- Manage users, roles, policies, or SSO
- Delete content or assets by default

Status rules:

- New items default to `draft`
- Create validation only allows `draft`
- Update validation allows `draft`, `in_review`, `published`, and `archived`
- Publishers are the approval gate for moving reviewed content to `published`

### CMS Administrator

- Directus role: `CMS Administrator`
- Directus policy: `CMS Administrator Policy`
- Intended users: platform owners and technical administrators
- Studio access: yes
- Admin/settings access: yes

Allowed actions:

- Full Directus administration
- Access control, schema, flows, settings, and integrations

### Catalogue Operator

- Directus role: `Catalogue Operator`
- Directus policy: `Catalogue Operator Policy`
- Intended users: operators managing backend-owned products, categories, brands, and merchandising bridge reads through the Directus operator module
- Studio access: yes
- Admin/settings access: no

Allowed actions:

- use the `Storefront Ops` Directus module
- read backend-owned catalogue entities through the bridge
- create, update, and delete backend-owned products, categories, and brands through the bridge

Not allowed:

- manage Directus schema or security
- publish CMS content unless separately mapped to a publisher/admin role

### Inventory Operator

- Directus role: `Inventory Operator`
- Directus policy: `Inventory Operator Policy`
- Intended users: operators responsible for variants, pricing, stock changes, and idempotent inventory adjustments
- Studio access: yes
- Admin/settings access: no

Allowed actions:

- use the `Storefront Ops` Directus module
- read backend-owned catalogue entities needed for variant/inventory work
- create and update product variants through the bridge
- apply inventory adjustments through the bridge

Not allowed:

- manage Directus schema or security
- change CMS publication state unless separately mapped to a publisher/admin role

### Public Access

Directus has a built-in public policy. It must remain deny-by-default except for explicitly public CMS collections.

Public policy rule:

- `read` only
- only on `DIRECTUS_CMS_PUBLIC_COLLECTIONS`
- filtered by `status = published`

This keeps drafts and review-stage content out of anonymous API access.

## Enforcement In Local Dev

The local governance bootstrap scripts [directus-published-at-bootstrap.sh](/Users/freddycooper/Documents/eshop/scripts/directus-published-at-bootstrap.sh) and [directus-sso-bootstrap.sh](/Users/freddycooper/Documents/eshop/scripts/directus-sso-bootstrap.sh) enforce this baseline by:

- creating the `published_at` database trigger across the governed public collection set
- seeding `CMS Editor`, `CMS Publisher`, `CMS Administrator`, `Catalogue Operator`, and `Inventory Operator`
- seeding matching Directus policies and role-policy junctions
- granting least-privilege system-collection access for files and folders
- granting editor and publisher permissions across the configured CMS collection allowlist
- granting public read-only permissions on the configured public collection allowlist with `status = published`

Because Directus permissions are stored by collection name, these rules can be provisioned before the collections physically exist. If the implemented schema diverges from these approved ids, update the env values and rerun the bootstrap.

The full process and editor/publisher responsibilities are documented in [directus-editorial-workflow.md](./directus-editorial-workflow.md).

## Review Notes

- The current local Keycloak realm maps `admin` to `CMS Administrator`, `manager` to `CMS Editor`, `publisher` to `CMS Publisher`, `catalogue_operator` to `Catalogue Operator`, and `inventory_operator` to `Inventory Operator`.
- Grant exactly one of the mapped Keycloak realm roles to a Directus user so the assigned Directus role stays deterministic.
- Keep the break-glass Directus local admin separate from Keycloak identities.
- Treat `legal_documents`, site-wide seller/contact details in `site_settings`, and any pricing or offer disclaimer copy as sensitive content. Those changes should always go through `in_review`, publisher approval, and a preview or staging check before publication.
- Public asset permissions should stay conservative until the media/folder model is defined. This document only grants public item reads for published CMS collections.
- The workflow scaffold also adds `published_at` to each governed collection. The governance bootstrap now manages it automatically, and `scripts/directus-content-audit.js` fails published records where it is missing.
