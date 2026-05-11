# Directus Storefront Ops Module

Storefront Ops is a production back-office application embedded in Directus Studio. Treat it as an operational admin surface, not as lightweight CMS configuration.

## Ownership

- Directus owns editorial content: pages, sections, navigation, banners, overlays, curated collections, FAQ, posts, legal documents, and site settings.
- The backend owns commerce records: products, categories, brands, variants, inventory, orders, payments, promotions, tax, and analytics.
- Storefront Ops may edit backend-owned records only through the `/storefront-ops-bridge` endpoint and the backend `/internal/directus/*` bridge controllers.

## Access Policy

The shared JavaScript access policy lives in `directus/extensions/storefront-ops-access-policy.js` and is consumed by:

- `directus-endpoint-storefront-ops`
- `directus-module-storefront-ops`
- `directus-panel-storefront-ops-launcher`

The backend enforces the same role boundary with `DirectusStorefrontOpsRolePolicy`. Directus-side checks are for user experience and early rejection; backend checks are the authority for bridge writes.

The backend reads the same `DIRECTUS_STOREFRONT_OPS_*_ROLE_IDS` environment variables as the Directus endpoint unless an `app.directus.roles.*` Spring property explicitly overrides them.

## Module Structure

The module remains the embedded Directus back-office shell, but shared concerns should live outside `module.vue`:

- `src/storefront-ops-tabs.js` owns tab/domain metadata, master-detail classification, and navigation/loading state shapes.
- `src/composables/storefrontOpsApi.js` owns Directus and bridge request shaping.
- `src/storefront-ops-formatters.js` owns shared formatting, filtering, parameter, money/date, and payload-normalization helpers.
- `directus/extensions/storefront-ops-preview.js` owns storefront preview URL construction.

Domain shell components live under `src/components/tabs/`. Keep API calls, cache invalidation, dirty-state decisions, and route persistence in the parent module or composables; tab components should receive existing state/forms and callbacks as props.

## Preview

Storefront Ops reads `STOREFRONT_OPS_PREVIEW_BASE_URL` from the endpoint access profile and exposes preview buttons for:

- home page content
- product storefront pages
- category storefront pages

Preview URLs open the canonical storefront route with `cmsPreview=1`. The storefront must honor that flag and call authenticated `/content/preview/*` APIs for draft-specific rendering; otherwise the link still opens the live route for layout verification.

## Cache Invalidation

Storefront Ops invalidates backend CMS cache after saving homepage content and curated collections. The backend now treats Redis invalidation failures as recoverable: it logs the failure and returns `deletedKeys=0` instead of failing the editorial save path.

## Dependency Hygiene

The extension packages use `@directus/extensions-sdk@17.1.4`, the latest compatible Directus 11 SDK version used during this hardening pass. Safe `npm audit fix` updates remove the resolved `lodash-es` and XML parser/builder advisories, but npm still reports upstream Directus SDK transitive findings through `axios@1.15.0` and `unhead@1.11.20`. Do not run `npm audit fix --force`: npm proposes `@directus/extensions-sdk@10.1.0`, which is a breaking downgrade for the Directus 11 extension host.

The build may still surface the SDK-owned Vite/esbuild peer mismatch (`vite@8.0.8` expects `esbuild@^0.27 || ^0.28`, while the SDK pins `esbuild@0.26.0`). Remove this note after a newer compatible `@directus/extensions-sdk` resolves both the remaining audit findings and the peer mismatch.
