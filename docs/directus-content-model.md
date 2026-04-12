# Directus Content Model Specification

This document defines the approved phase-1 Directus schema for the Cozyhome CMS surface.

## Modeling Decision

Phase 1 should use a typed `page_sections` collection, not a full many-to-any reusable block system.

Why:

- the initial section set is small and known from the current storefront
- a single ordered section collection keeps frontend queries simpler
- permissions are easier to reason about because content stays inside a bounded collection set
- editors can still get a clean UI by using conditional field visibility based on `section_type`

Revisit many-to-any reusable blocks only if marketing needs a materially larger section library or true cross-page block reuse.

## Shared Conventions

- All public-facing collections include `status` with values `draft`, `published`, `archived`.
- All public-facing collections include `published_at`.
- Routable collections use unique `slug` and, where needed, unique `path`.
- Ordered collections use a `sort` integer.
- Directus system fields `date_created`, `date_updated`, `user_created`, and `user_updated` should remain enabled for auditability.
- Public storefront reads should filter to `status = published`.
- Do not model commerce data in Directus. Product/category references remain opaque backend IDs or slugs only.

## Collection List

Primary collections:

- `site_settings` (singleton)
- `navigation`
- `page`
- `page_sections`
- `faq`
- `legal_documents`
- `banner`
- `post` (optional)

Support collections:

- `navigation_items`
- `page_section_items`

## Relationship Summary

- `site_settings.announcement_banner` -> M2O `banner`
- `navigation.items` -> O2M `navigation_items`
- `navigation_items.page` -> optional M2O `page`
- `page.sections` -> O2M `page_sections`
- `page_sections.items` -> O2M `page_section_items`
- `page_sections.banners` -> M2M `banner`
- `page_sections.faqs` -> M2M `faq`
- `page_sections.legal_documents` -> M2M `legal_documents`
- `page_sections.posts` -> M2M `post`

## Collection Specs

### `site_settings`

- Singleton: yes
- Public read: yes
- Purpose: global brand, footer, seller, and SEO defaults

Recommended fields:

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `status` | string enum | yes | Keep the singleton in `published` for storefront reads. |
| `site_name` | string | yes | Maps to current `legalTokens.SITE_NAME`. |
| `site_tagline` | string | no | Short brand line for future header/footer use. |
| `brand_description` | text | yes | Footer/about brand description. |
| `support_phone` | string | yes | Customer-facing contact phone. |
| `support_email` | string | yes | Customer-facing contact email. |
| `legal_entity_short` | string | yes | Current footer/legal short seller name. |
| `legal_entity_full` | string | yes | Full legal entity name. |
| `legal_inn` | string | yes | Seller INN. |
| `legal_ogrnip` | string | yes | Seller OGRNIP. |
| `legal_address` | text | yes | Seller legal address. |
| `copyright_start_year` | integer | yes | Current footer start year is `2015`. |
| `payment_badges` | json or multi-select | no | Start with `mir`, `visa`, `mastercard`. |
| `default_seo_title_suffix` | string | yes | Usually same as site name. |
| `default_seo_description` | text | yes | Fallback for pages without explicit SEO copy. |
| `default_robots` | string enum | yes | Default `index,follow`. |
| `default_og_image` | file | no | Fallback OG/Twitter share image. |
| `announcement_banner` | relation to `banner` | no | Filter to `banner_type = announcement`. |

### `navigation`

- Singleton: no
- Public read: yes
- Purpose: editor-managed navigation groups for header/footer/manual link sets

Recommended fields:

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `status` | string enum | yes | Publish/unpublish entire navigation groups. |
| `key` | string unique | yes | Example: `footer_catalog`, `footer_service`, `footer_account`, `footer_legal`. |
| `title` | string | yes | Group title shown in UI. |
| `placement` | string enum | yes | `header`, `footer`, `legal`, `utility`. |
| `description` | text | no | Editor-facing guidance. |
| `sort` | integer | no | Controls group order within the placement. |
| `items` | O2M alias | yes | Relation to `navigation_items`. |

### `navigation_items`

- Singleton: no
- Public read: yes
- Purpose: individual links inside a navigation group

Recommended fields:

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `status` | string enum | yes | Allows hiding a single item without unpublishing the whole group. |
| `navigation` | M2O `navigation` | yes | Parent group. |
| `label` | string | yes | Link text. |
| `item_type` | string enum | yes | `internal_page`, `internal_path`, `external_url`, `anchor`. |
| `page` | optional M2O `page` | no | Preferred for CMS-owned internal pages. |
| `url` | string | conditional | Required for external or manual paths. |
| `open_in_new_tab` | boolean | no | Mainly for external URLs. |
| `visibility` | string enum | no | `all`, `guest_only`, `authenticated_only`. |
| `badge_text` | string | no | Small helper text like “new”. |
| `sort` | integer | no | Item order within the group. |

Rule:

- Category trees and catalog taxonomies stay backend-owned and must not be duplicated here.

### `page`

- Singleton: no
- Public read: yes
- Purpose: routable editorial and service pages, including the homepage and legal hub

Recommended fields:

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `status` | string enum | yes | Publishing gate for the page. |
| `title` | string | yes | Internal and public page title. |
| `slug` | string unique | yes | Stable CMS identifier. |
| `path` | string unique | yes | Exact storefront path, for example `/`, `/info/delivery`, `/info/legal`. |
| `template` | string enum | yes | `home`, `content`, `legal_hub`, `faq`, `landing`. |
| `nav_label` | string | no | Shorter label for menus. |
| `summary` | text | no | Intro text or editor excerpt. |
| `seo_title` | string | no | Optional override. |
| `seo_description` | text | no | Optional override. |
| `seo_image` | file | no | Optional OG image override. |
| `robots` | string | no | Rare override such as `noindex,nofollow`. |
| `sections` | O2M alias | yes | Relation to `page_sections`. |
| `published_at` | datetime | no | Useful for scheduled publishing or change logs. |

Rule:

- The homepage is a normal `page` row with `path = '/'` and `template = home`.

### `page_sections`

- Singleton: no
- Public read: yes
- Purpose: ordered, typed sections that compose a page

Recommended fields:

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `status` | string enum | yes | Section-level publish control. |
| `page` | M2O `page` | yes | Parent page. |
| `internal_name` | string | yes | Editor label, not shown publicly. |
| `section_type` | string enum | yes | `hero`, `rich_text`, `feature_list`, `banner_group`, `newsletter_cta`, `faq_list`, `legal_documents_list`, `post_list`, `product_reference_list`. |
| `sort` | integer | yes | Order on the page. |
| `anchor_id` | string | no | For in-page navigation. |
| `eyebrow` | string | no | Small uppercase label. |
| `title` | string | no | Main section heading. |
| `accent` | string | no | Accent word or phrase, used by hero sections. |
| `body` | rich text | no | General body copy. |
| `image` | file | no | Section image. |
| `image_alt` | string | no | Alt text override for the section image. Falls back to file metadata if empty. |
| `mobile_image` | file | no | Optional mobile-specific image. |
| `mobile_image_alt` | string | no | Alt text override for the mobile image. Falls back to file metadata if empty. |
| `primary_cta_label` | string | no | |
| `primary_cta_url` | string | no | |
| `secondary_cta_label` | string | no | |
| `secondary_cta_url` | string | no | |
| `style_variant` | string enum | no | Example: `default`, `warm`, `sage`, `quiet`, `legal`. |
| `layout_variant` | string enum | no | Example: `contained`, `full`, `two_column`, `cards`. |
| `items` | O2M alias | no | Relation to `page_section_items`. |
| `banners` | M2M `banner` | no | Used when `section_type = banner_group`. |
| `faqs` | M2M `faq` | no | Used when `section_type = faq_list`. |
| `legal_documents` | M2M `legal_documents` | no | Used when `section_type = legal_documents_list`. |
| `posts` | M2M `post` | no | Used when `section_type = post_list`. |

Editor UI rule:

- Configure Directus field conditions so only the relevant relation fields and presentation fields show for the chosen `section_type`.

### `page_section_items`

- Singleton: no
- Public read: yes
- Purpose: small repeatable cards/links inside a page section

Recommended fields:

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `status` | string enum | yes | Allows hiding individual cards. |
| `page_section` | M2O `page_sections` | yes | Parent section. |
| `title` | string | yes | Card or highlight title. |
| `description` | text | no | Subtitle/body text. |
| `label` | string | no | Button or badge text. |
| `url` | string | no | Link target for highlight cards. |
| `icon_name` | string | no | Optional icon token. |
| `image` | file | no | Optional card image. |
| `image_alt` | string | no | Alt text override for the item image. Falls back to file metadata if empty. |
| `reference_kind` | string enum | no | `none`, `product_slug`, `category_slug`, `external_url`. |
| `reference_key` | string | no | Backend slug/id when the item points to commerce data. |
| `sort` | integer | yes | Item order within the section. |

Use cases:

- hero highlights
- brand value cards
- quick links
- curated backend product/category references without moving commerce ownership into CMS

### `faq`

- Singleton: no
- Public read: yes
- Purpose: reusable FAQ entries for future FAQ pages and inline FAQ sections

Recommended fields:

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `status` | string enum | yes | Publish gate. |
| `question` | string | yes | |
| `answer` | rich text | yes | |
| `category` | string | no | Example: `delivery`, `payment`, `returns`, `care`. |
| `slug` | string unique | no | Optional if FAQ entries need anchorable URLs later. |
| `is_featured` | boolean | no | Useful for homepage or landing-page subsets. |
| `sort` | integer | no | Default list order. |

### `legal_documents`

- Singleton: no
- Public read: yes
- Purpose: seller documents, privacy, cookie, consent, and offer content

Recommended fields:

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `status` | string enum | yes | Publish gate. |
| `document_key` | string unique | yes | Stable code such as `privacy-policy` or `sales-terms`. |
| `title` | string | yes | Public title. |
| `slug` | string unique | yes | Human-friendly URL part if needed. |
| `path` | string unique | yes | Current storefront already uses explicit paths. |
| `summary` | text | yes | Used on the legal hub and SEO description fallback. |
| `document_type` | string enum | yes | `privacy_policy`, `user_agreement`, `pd_consent`, `ads_consent`, `cookie_policy`, `sales_terms`, `seller_details`, `other`. |
| `version_label` | string | yes | Example `v2026-04`. |
| `effective_at` | datetime | yes | Legal effective date. |
| `body` | rich text | yes | Primary rendered content. |
| `attachment` | file | no | Optional PDF or signed copy. |
| `show_in_index` | boolean | yes | Controls listing on the legal hub. |
| `sort` | integer | no | Index order. |
| `seo_title` | string | no | Optional override. |
| `seo_description` | text | no | Optional override. |

### `banner`

- Singleton: no
- Public read: yes
- Purpose: announcement bars and reusable promo banners

Recommended fields:

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `status` | string enum | yes | Publish gate. |
| `banner_type` | string enum | yes | `announcement`, `promo`. |
| `internal_name` | string | yes | Editor-facing label. |
| `short_text` | string | no | Used for sitewide announcement bars. |
| `eyebrow` | string | no | |
| `title` | string | no | Main promo heading. |
| `description` | text | no | |
| `image` | file | no | Optional promo image. |
| `mobile_image` | file | no | Optional mobile asset. |
| `primary_cta_label` | string | no | |
| `primary_cta_url` | string | no | |
| `secondary_cta_label` | string | no | |
| `secondary_cta_url` | string | no | |
| `style_variant` | string enum | no | Replace hardcoded Tailwind gradients with approved variants. |
| `active_from` | datetime | no | Optional schedule start. |
| `active_to` | datetime | no | Optional schedule end. |
| `sort` | integer | no | Default ordering in banner groups. |

### `post` (optional)

- Singleton: no
- Public read: yes
- Purpose: optional blog/news surface

Recommended fields:

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `status` | string enum | yes | Publish gate. |
| `title` | string | yes | |
| `slug` | string unique | yes | |
| `excerpt` | text | yes | Card summary. |
| `body` | rich text | yes | |
| `cover_image` | file | no | |
| `author_name` | string | no | Simple author field is enough for phase 1. |
| `published_at` | datetime | no | |
| `is_featured` | boolean | no | |
| `seo_title` | string | no | |
| `seo_description` | text | no | |

## Section-Type Mapping To Current Frontend

The initial `page_sections.section_type` values should map to current storefront needs:

| Section Type | Current Usage |
| --- | --- |
| `hero` | homepage hero banner |
| `feature_list` | homepage trust links, brand values |
| `banner_group` | homepage promo banners |
| `newsletter_cta` | newsletter block |
| `rich_text` | delivery/payment/bonuses/production/info pages |
| `faq_list` | future FAQ page or inline FAQ block |
| `legal_documents_list` | legal hub cards |
| `post_list` | optional blog/news teaser grid |
| `product_reference_list` | curated references to backend products/categories by slug/id |

## Explicit Non-Goals

- No catalog, variant, stock, or price modeling in Directus.
- No customer, order, payment, shipment, or consent evidence records in Directus.
- No attempt to make Directus the source of truth for category trees.
- No schema split by locale in phase 1. Add Directus translations later if localization becomes real scope.

## Implementation Follow-Up

The version-controlled schema snapshot at `directus/schema/schema.snapshot.json` is now the source of truth for these collections. Local bootstrap applies that committed snapshot instead of rebuilding the schema imperatively.

When the full Directus schema implementation task starts:

1. Make schema changes against a local Directus instance using the ids in this document.
2. Export the updated snapshot with `./scripts/directus-schema-snapshot.sh` and commit the snapshot diff.
3. Apply the committed snapshot in target environments with `./scripts/directus-schema-apply.sh`.
4. Keep public permissions on the approved public collection set with `status = published`.
5. Configure conditional field visibility on `page_sections` by `section_type`.
6. Update the bootstrap allowlists only if implemented ids differ.
