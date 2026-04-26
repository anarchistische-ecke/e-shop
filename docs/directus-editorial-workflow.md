# Directus Editorial Workflow

This document defines the operating workflow for CMS-managed content in Directus.

## Goal

Keep authoring simple for non-technical editors while making publication an explicit approval step.

## Workflow States

Every public CMS collection uses the same `status` values:

- `draft`: working state for authoring and revisions
- `in_review`: submitted to a publisher for approval
- `published`: approved for public storefront delivery
- `archived`: retired content that should not be shown publicly or in preview

The companion `published_at` field records when the current revision was approved for publication. The governance bootstrap stamps it automatically when an item enters `published`, clears it when an item leaves `published`, and the content audit fails published records where it is missing.

## Roles In The Process

### Editor

- Creates and edits content in `draft`
- Submits content for approval by changing `status` to `in_review`
- Can continue revising content in `draft` or `in_review`
- Cannot publish or archive content

### Publisher

- Reviews content in `in_review`
- Publishes approved content by changing `status` to `published`
- Relies on the automatic `published_at` stamp added during approval
- Returns content to `draft` when changes are required
- Archives content when it should be removed from delivery

### Administrator

- Maintains schema, permissions, SSO mappings, and operational settings
- Does not participate in the normal editorial path unless acting as an exception handler

Sensitive-content rule:

- `legal_documents`, seller/contact details in `site_settings`, returns-policy copy, and pricing or offer disclaimers always require publisher approval and a preview or staging check before publication.

## Standard Path

1. Editor creates or updates content with `status = draft`.
2. Editor previews the draft using the protected backend preview endpoints.
3. Editor changes `status` to `in_review` when the item is ready for approval.
4. Publisher reviews the item in Directus and in preview.
5. Publisher either:
   - returns the item to `draft` for more work, or
   - sets `status = published`, which automatically stamps `published_at`
6. Public storefront routes continue to read only `published` items.

## In-Product Enforcement

The workflow is enforced by the committed Directus schema snapshot and the governance bootstrap:

- `status` fields show the approved workflow states directly in Directus Studio
- `published_at` is present on every governed public collection
- `CMS Editor` permissions:
  - create only with `status = draft`
  - update only when the resulting status is `draft` or `in_review`
- `CMS Publisher` permissions:
  - create only with `status = draft`
  - update only when the resulting status is `draft`, `in_review`, `published`, or `archived`
- public policy:
  - read only
  - filtered to `status = published`

This means the approval step is not just a convention. Editors cannot move content directly to `published`, and anonymous reads cannot see `draft` or `in_review`.

## Preview Behavior

Protected preview routes under `/content/preview/*` are intended for editors and publishers. They may read any non-archived CMS content so review can happen before publication.

Public storefront routes under `/content/*` remain published-only.

## Operational Notes

- Use `archived` only for retired content, not as a temporary review state.
- If a publisher rejects a submission, return it to `draft` instead of leaving it in `in_review`.
- If a publisher sees a missing or incorrect `published_at`, treat that as a governance/bootstrap defect and rerun the bootstrap or investigate direct DB access outside Directus.
- If a collection is added to the CMS scope, include the same `status` and `published_at` fields and rerun the governance bootstrap.
