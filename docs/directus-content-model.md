# Directus Marketing V2 Content Model

Native Directus Content is the canonical marketing workspace. The custom
Storefront Ops module remains authoritative for commerce operations. Directus
11.17.2 is the tested release line for this model.

## Ownership boundary

Directus owns presentation:

- pages and the homepage;
- page blocks and editorial rich text;
- campaigns and banner creatives;
- curated storefront collections;
- navigation, FAQ, legal documents;
- global SEO, OG, seller, and support presentation.

The backend owns products, categories, brands, prices, stock, discount rules,
eligibility, promotion activation, promo-code behavior, cart, and checkout.
CMS records may select stable backend IDs/slugs through the Storefront Ops
picker, but may not redefine operational promotion facts.

## Authoring groups

- `cms_marketing` (`Маркетинг`): `campaign`, `banner`,
  `storefront_collection`.
- `cms_site_content` (`Контент сайта`): `page`, `navigation`, `faq`,
  `legal_documents`, `site_settings`.

The `post` collection is unused and hidden. Junctions, section items,
compatibility keys, and migration keys are hidden from ordinary editors.

## Shared lifecycle

Public content uses `draft`, `in_review`, `published`, and `archived`, with
`published_at`, Directus revisions, and content versioning. Public reads require
`status = published`. The scoped storefront-reader policy has no Directus app
access. Anonymous item permissions are removed.

## Relationships

- `site_settings.announcement_banner` → `banner`
- `navigation.items` → `navigation_items`
- `navigation_items.page` → `page`
- `page.sections` → ordered `page_sections`
- `page_sections.items` → ordered `page_section_items`
- `page_sections.banners` ↔ `banner` through `page_section_banners`
- `page_sections.faqs` ↔ `faq` through `page_section_faqs`
- `page_sections.legal_documents` ↔ `legal_documents` through
  `page_section_legal_documents`
- `campaign.banners` → ordered `banner`
- `campaign.landing_page` → `page`
- `campaign.storefront_collection` → `storefront_collection`

## Page builder

`page_sections` remains a typed in-place model. The Directus interface exposes
only fields relevant to `section_type` and supports ordered editing.

Supported types are `hero`, `rich_text`, `feature_cards`, `banner_group`,
`campaign_slot`, `collection_rail`, `product_references`,
`category_references`, `brand_references`, `faq`,
`legal_document_list`, and `cta`.

Rich text is authored through WYSIWYG fields and sanitized by the backend with
an explicit HTML allowlist. The Directus toolbar is deliberately limited to
headings, emphasis, lists, quotations, separators, and links; source editing,
embedded media, arbitrary colors/fonts, and tables are not exposed.
CTA/navigation URLs accept internal paths, HTTPS, `mailto:`, and `tel:` only.
Desktop/mobile media require corresponding alt text.

Commerce reference fields are fixed picker interfaces:

- `product_key`
- `category_key`
- `brand_key`
- `promotion_id`
- `promo_code_id`
- `storefront_collection`

Hidden raw fields remain temporarily for backward compatibility and migration.

## Campaigns and creatives

`campaign` contains internal name, slug, lifecycle status, priority, ordering,
UTC activation window, optional landing page/collection, optional operational
link kind, promotion ID, promo-code ID, and related creatives.

`banner` contains campaign, placement, copy, desktop/mobile media and alt text,
primary/secondary CTA, approved style/layout variants, priority/order, and an
optional effective window.

Placements are:

- `sitewide_announcement`
- `home_hero`
- `home_promo`
- `home_highlight`
- `page_inline`

At delivery time, both records must be published and effective. Start is
inclusive and end is exclusive. A linked backend promotion/promo code must be
active. Ordering is campaign priority, configured sort, and stable ID; creative
ordering follows the same rule. Only one site announcement is returned and
campaign slots default to two.

Campaign responses and pages containing campaign slots are not browser-stale.
The facade re-evaluates time windows and operational status on every request.
Ordinary CMS content retains configured `stale-while-revalidate` and
`stale-if-error` behavior.

## Delivery contracts

Backward-compatible routes:

- `GET /content/site-settings`
- `GET /content/navigation`
- `GET /content/pages/{slug}`
- `GET /content/collections/{key}`

Marketing V2 routes:

- `GET /content/campaigns/active?placement=…&limit=…`
- `GET /content/campaigns/{slug}`
- `GET /content/legal-documents/{key-or-slug}`
- `GET /content/preview/session` with a signed preview header
- `POST /internal/directus/content/cache/invalidate`

The storefront route `/promo/:slug` is SSR. Directus preview URLs first hit the
authenticated Storefront Ops endpoint, which signs a short-lived page,
campaign, or banner claim. The storefront stores it in an HttpOnly,
SameSite=Lax cookie and asks the backend to load the selected Directus version
with a dedicated server-side preview reader token. The ordinary storefront
reader remains restricted to published rows.

## Provisioning artifacts

- Schema: `directus/schema/schema.snapshot.json`
- Additive schema modifier: `scripts/directus-marketing-v2-schema.js`
- Picker extension:
  `directus/extensions/directus-interface-storefront-entity-picker`
- Date-window validation hook:
  `directus/extensions/directus-hook-marketing-validation`
- Governance/security: `scripts/directus-governance-bootstrap.sh`
- Saved views: `scripts/directus-marketing-v2-presets.js`
- Flows: `scripts/directus-marketing-v2-flows.js`
- Idempotent migration: `scripts/directus-marketing-v2-migrate.js`
- Rollout and acceptance: `docs/directus-marketing-v2-rollout.md`
