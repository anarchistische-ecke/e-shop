# Directus Content Migration Plan

This project seeds Directus with an initial editorial dataset taken from the current storefront’s hard-coded content.

## Goal

Move the initial non-commerce content into Directus without turning content migration into one-off clickops.

The adopted method is:

- keep a committed seed dataset in `directus/seed/initial-content.js`
- keep the current legal HTML templates in `directus/seed/legal/`
- run `scripts/directus-content-import.sh` against local, staging, or production Directus
- review seed changes in PRs before rerunning the import

The script upserts by stable keys, so it is safe to rerun in staging after schema refreshes.

## Source Mapping

The current source content came from these storefront files:

- `/Users/freddycooper/Downloads/cozyhome/src/components/Footer.js`
- `/Users/freddycooper/Downloads/cozyhome/src/data/legal/constants.js`
- `/Users/freddycooper/Downloads/cozyhome/src/pages/AboutPage.js`
- `/Users/freddycooper/Downloads/cozyhome/src/pages/DeliveryInfoPage.js`
- `/Users/freddycooper/Downloads/cozyhome/src/pages/PaymentInfoPage.js`
- `/Users/freddycooper/Downloads/cozyhome/src/pages/BonusesInfoPage.js`
- `/Users/freddycooper/Downloads/cozyhome/src/pages/ProductionInfoPage.js`
- `/Users/freddycooper/Downloads/cozyhome/src/pages/LegalInfoPage.js`
- `/Users/freddycooper/Downloads/cozyhome/src/pages/legal/LegalDocumentPage.js`
- `/Users/freddycooper/Downloads/cozyhome/public/legal/*.html`

## What Gets Imported

- `site_settings`
  - brand, support, legal-entity, and default SEO fields
- `navigation`
  - the current footer groups and their links
- `page`
  - about, delivery, payment, bonuses, production, legal hub, and FAQ hub
- `page_sections`
  - structured sections for each seeded page
- `page_section_items`
  - stats and feature-card items inside seeded sections
- `faq`
  - starter FAQ entries derived from existing service/legal copy
- `legal_documents`
  - the full current legal HTML templates with seller/contact tokens rendered

## Notes On Inference

The current storefront does not have a dedicated FAQ page or FAQ data source. The seeded FAQ entries are an editorial inference from the already published delivery, payment, bonuses, and legal copy. They are intended as a starting dataset, not a claim that a formal FAQ already existed.

The current public router also does not expose an about route. The seed creates an `/about` CMS page anyway so the content is preserved and ready for later frontend wiring.

## How To Run

Local:

```bash
cd /Users/freddycooper/Documents/eshop
./scripts/directus-content-import.sh
```

Dry run:

```bash
cd /Users/freddycooper/Documents/eshop
./scripts/directus-content-import.sh --dry-run
```

Staging or production with a different env file:

```bash
cd /Users/freddycooper/Documents/eshop
./scripts/directus-content-import.sh --env-file /path/to/directus.env
```

If the target VM does not have a host Node runtime, run the importer inside the deployed Directus container instead:

```bash
cd <deploy-path>
docker compose --env-file .env -f docker-compose.prod.yml exec -T \
  -e DIRECTUS_BASE_URL=http://127.0.0.1:8055 \
  directus node /opt/directus-deploy/scripts/directus-content-import.js
```

Optional prune mode removes previously seeded records whose `migration_key` starts with `initial:` but no longer exists in the current seed set:

```bash
cd /Users/freddycooper/Documents/eshop
./scripts/directus-content-import.sh --prune
```

## Rerunnable Keys

The importer updates records in place using these stable keys:

- `site_settings`: first singleton row
- `navigation`: `key`
- `navigation_items`: `migration_key`
- `page`: `path`
- `page_sections`: `migration_key`
- `page_section_items`: `migration_key`
- `faq`: `migration_key`
- `legal_documents`: `document_key`

## Review Workflow

1. Edit `directus/seed/initial-content.js` or `directus/seed/legal/*.html`.
2. Run `./scripts/directus-content-import.sh --dry-run`.
3. Apply against local Directus and review in Studio.
4. Commit the seed changes in git.
5. Rerun the importer in staging after the schema snapshot has been applied.

This keeps initial content changes visible in PRs and makes staging reprovisioning predictable.
