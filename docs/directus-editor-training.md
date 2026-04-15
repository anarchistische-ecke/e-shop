# Directus Editor Training Session

This session plan is the recommended baseline for onboarding non-technical content editors to the Cozyhome CMS.

Target duration: `75 minutes`

Recommended attendees:

- CMS Editors
- CMS Publisher / Reviewer
- one CMS Administrator or trainer

Use staging or local Directus for the live demo. Do not use production for the hands-on exercises.

## Session Goals

By the end of the session, an editor should be able to:

- sign in through Keycloak SSO
- identify which collection to use for a given change
- edit a page, FAQ item, and navigation link
- upload or select media safely
- move work from `draft` to `in_review`
- understand when a publisher is required
- recognize that legal, policy, seller-details, and disclaimer changes always need publisher review plus preview/staging validation
- know how to request help

## Agenda

1. `10 min` Login, roles, and Studio orientation
2. `10 min` Content map: what lives in `site_settings`, `navigation`, `page`, `faq`, `legal_documents`, and `banner`
3. `15 min` Page editing: title, slug, sections, SEO fields, and status
4. `10 min` Navigation and footer editing
5. `10 min` FAQ and legal document editing
6. `10 min` Media library basics: upload, alt text, and safe reuse
7. `10 min` Workflow: `draft -> in_review -> published`, review expectations, and support path

## Trainer Prep Checklist

- Confirm Directus Studio URL is reachable.
- Confirm Keycloak SSO is working for one editor account and one publisher account.
- Confirm the seeded CMS collections exist and contain sample content.
- Confirm the editor guide at [directus-editor-guide.md](./directus-editor-guide.md) is available to attendees.
- Prepare one safe practice item in staging or local Directus for live editing.
- Confirm the support intake path from [directus-editor-support-process.md](./directus-editor-support-process.md).

## Hands-On Exercises

Run these during the session:

1. Editor signs in with Keycloak.
2. Editor opens `Page`, edits the `delivery` page summary, and saves as `draft`.
3. Editor opens `Navigation`, updates one footer or header link label, and saves.
4. Editor opens `FAQ`, updates one answer, and moves it to `in_review`.
5. Publisher reviews the changed item and publishes it.
6. Group verifies the expected storefront result in staging or preview.

## Trainer Notes

- Keep the boundary clear: editors manage CMS content only. Product catalog, orders, customers, pricing, and stock stay backend-owned.
- Show one real example per collection instead of touring every field.
- Emphasize status changes. Editors should not treat `published` as a save button.
- Show the difference between `draft`, `in_review`, and `published` on an actual item.
- Call out `Legal Documents`, returns-policy copy, seller/contact details, and pricing/offer disclaimers as sensitive content that always requires publisher review and preview/staging verification.
- Use the same vocabulary as the written guide so support requests are consistent.

## Exit Criteria

The session is complete when each attendee can do the following without assistance:

- log in through Keycloak
- find the right collection for a routine content change
- save a change in `draft`
- submit a change for review
- explain when to involve a publisher or administrator

## Follow-Up Materials

- [directus-editor-guide.md](./directus-editor-guide.md)
- [directus-editor-onboarding.md](./directus-editor-onboarding.md)
- [directus-editorial-workflow.md](./directus-editorial-workflow.md)
- [directus-editor-support-process.md](./directus-editor-support-process.md)
