# Directus Marketing V2 — Editor Guide

This is the day-to-day workflow for the `Контент-менеджер` role. Native
Directus Content is the source of truth for website presentation. The
`Управление витриной` custom module remains the source of truth for products,
prices, stock, promotion rules, promo codes, orders, and other commerce
operations.

## Where content lives

The Content sidebar is organized into two groups:

- **Маркетинг:** `Кампании`, `Баннеры и креативы`, and `Подборки витрины`.
- **Контент сайта:** `Страницы`, `Навигация`, `FAQ`,
  `Юридические документы`, and `Настройки сайта`.

Technical junction collections and migration fields are intentionally hidden.
Do not type product, category, brand, promotion, or promo-code identifiers:
use the searchable storefront picker on the relevant field.

The native `Страницы` → `Главная` item is the canonical homepage editor. The
legacy Home tab in `Управление витриной` is a rollback surface and is hidden
while Marketing V2 is enabled.

## Statuses and versions

- `draft`: working copy.
- `in_review`: ready for a final content check.
- `published`: visible to public storefront reads.
- `archived`: retired from the storefront.

The Content Manager may publish every CMS surface directly, including legal
documents. Directus revisions and content versions remain the audit trail.
For a material campaign, homepage, SEO, navigation, or legal change:

1. Create a named content version.
2. Make and save the change.
3. Use Live Preview from that selected version.
4. Confirm desktop and mobile media, links, dates, and copy.
5. Promote the version and set the item to `published`.

Preview links are short-lived. They establish an HttpOnly storefront preview
session and never expose the Directus service credential. If a link expires,
open Preview from Directus again.

## Editing the homepage and pages

Open `Страницы`, then select `Главная` or another page. Reorder blocks by drag
and drop. The block type controls which fields Directus shows.

Supported blocks:

- hero;
- rich text;
- feature cards;
- banner group;
- campaign slot;
- collection rail;
- product, category, and brand references;
- FAQ;
- legal-document list;
- CTA.

Use the rich-text editor for body copy. Only the approved formatting and safe
internal, HTTPS, mail, and telephone links are delivered to the storefront.
Script, event-handler, HTTP, protocol-relative, and data links are removed.

For every meaningful image:

- upload or select the desktop image;
- add required descriptive alt text;
- add a mobile image and its alt text when the crop differs;
- check the result in Live Preview at desktop and mobile widths.

`Slug` and `Path` are routing identifiers. Do not change them casually. Set
page robots metadata explicitly when a page must not be indexed.

## Running a campaign

Create the operational discount or promo code in `Управление витриной` first
when checkout behavior is required. Then create the presentation in
`Маркетинг` → `Кампании`.

Required campaign checks:

1. Give it an internal name, public slug, priority, and UTC start/end window.
2. Optionally choose a landing page or storefront collection.
3. If the campaign represents a real discount, use the promotion or promo-code
   picker. Never reproduce a discount value in CMS copy as the source of truth.
   Inactive, expired, and exhausted operational records are disabled in the
   picker; future `ACTIVE` records remain selectable for scheduled campaigns.
4. Add one or more related banner creatives.
5. Choose a placement:
   `sitewide_announcement`, `home_hero`, `home_promo`,
   `home_highlight`, or `inline_page`.
6. Supply desktop/mobile media, alt text, CTA, approved style/layout, priority,
   and any narrower creative window.
7. Preview, then publish both the campaign and each creative.

Activation uses UTC. Start is inclusive; end is exclusive. A linked backend
promotion or promo code must also be active. Storefront discount facts are
read from the backend, so displayed values remain aligned with checkout.

Only the highest-priority site announcement appears. Campaign slots default
to two cards. Use the saved views `Кампании · активные сейчас`,
`Кампании · запланированные`, and `Кампании · черновики` for routine work.

## Navigation, FAQ, SEO, and legal

- Prefer the `Страница` relation for CMS-owned navigation links. Use a manual
  URL only for approved internal or HTTPS external destinations.
- Reuse FAQ entries through the page block relation instead of copying text.
- Put global SEO defaults and the default OG image in `Настройки сайта`; page
  fields override them.
- Edit legal text in `Юридические документы`. Keep a clear version label,
  effective date, and change note before publishing.
- The legal hub uses a legal-document-list block. Reorder its relations rather
  than hard-coding links in rich text.

## Pre-publish checklist

- The parent page/campaign and every related child are published.
- Campaign and creative UTC windows are correct.
- Linked operational records are active.
- Images load, mobile crops work, and alt text is meaningful.
- CTA and navigation links resolve safely.
- SEO title, description, image, and robots value are correct.
- FAQ and legal relations show in the intended order.
- Live Preview shows the selected Directus version.
- A public/incognito request cannot see draft content or the preview URL.

If Marketing V2 data is absent, the storefront retains code fallbacks for
rollback and outages. Treat any fallback metric as an operational issue rather
than a normal editorial workflow.
