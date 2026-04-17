# Directus Integration Pattern Decision

Date: 2026-04-12

Status: approved

## Decision

Choose **Option 2: backend facade** for storefront CMS content.

The public frontend should fetch CMS content from the existing backend API origin, and the backend should read from Directus.

This means:

- browser -> backend API for CMS JSON
- backend -> Directus for published content reads
- Directus stays the editorial system and content source of truth

Direct asset delivery is not the main part of this decision. Directus `/assets` or object storage/CDN delivery can still be used for files later.

## Context

This codebase already centers the storefront around a single backend API origin:

- the frontend API client in `/Users/freddycooper/Downloads/cozyhome/src/api/index.js` already routes storefront data through `REACT_APP_API_BASE`
- commerce data, auth, and business logic already live behind the backend
- Directus is being added for editorial content, not as a replacement for backend-owned commerce data

The main architectural choice was whether published CMS content should be read:

- directly by the browser from Directus
- or through the backend as a facade/proxy

## Options Considered

### Option 1: frontend direct

The storefront calls Directus directly for published content.

Pros:

- less backend implementation at first
- fastest path for simple public reads
- can work with Directus public role or a read-only public token

Cons:

- adds a second API origin to the storefront
- preview and draft access become harder to control safely
- browser-facing Directus tokens or broader public permissions become part of the design
- frontend becomes tightly coupled to Directus response shape
- caching, transformation, and fallback logic get pushed into the browser
- cross-system pages that mix CMS content with backend-owned commerce references become more awkward

### Option 2: backend facade

The backend reads Directus and exposes storefront-facing CMS endpoints.

Pros:

- keeps a single API origin for the storefront
- keeps Directus tokens and preview logic server-side
- allows centralized caching, normalization, and fallback handling
- lets the backend resolve CMS references to backend-owned commerce data without leaking that complexity to the browser
- reduces frontend coupling to Directus collection/query details
- avoids introducing new Directus browser CORS requirements for the normal storefront path

Cons:

- adds backend implementation work
- CMS data contracts must be designed and maintained in the backend
- Directus field/model changes may require backend mapping updates

## Why Option 2 Was Chosen

Option 2 fits this project better because the storefront is already backend-centric, the CMS scope is intentionally separated from commerce ownership, and future preview/publishing controls are easier to enforce on the server side than in the browser.

It also matches the deployment posture better:

- one public storefront origin
- one backend API origin
- Directus can stay an internal/editorial integration point instead of becoming a second public application API for the storefront

## Resulting Boundary

The intended runtime boundary is:

- Directus owns editorial content entry and storage
- backend owns CMS-to-storefront response shaping
- frontend consumes backend CMS endpoints, not Directus collection endpoints

Examples of the intended backend-facing shape:

- `/cms/site-settings`
- `/cms/navigation`
- `/cms/pages/{path}`
- `/cms/legal-documents/{slug}`
- `/cms/faq`

Exact endpoint design is a later implementation task.

## Preview And Publishing Implications

Published content for the public storefront should be filtered server-side before being returned.

If preview is added later, it should also be handled through the backend, for example by:

- manager/admin-authenticated preview endpoints
- a signed preview parameter or short-lived preview token
- explicit draft-mode handling that never relies on a public browser token

## Environment Implications

This decision makes these backend variables the important production path:

- `DIRECTUS_BASE_URL`
- `DIRECTUS_STATIC_TOKEN` if authenticated backend reads are needed

The frontend Directus variables remain optional and are no longer the preferred production integration path:

- `REACT_APP_DIRECTUS_BASE_URL`
- `REACT_APP_DIRECTUS_PUBLIC_TOKEN`

Keep them only for temporary experiments, migrations, or admin-only tooling if explicitly needed later.

## Production Impact

No live production change is required by this decision record alone.

Later implementation will require:

- backend CMS client code and public CMS endpoints
- production backend env for Directus connection
- optional backend-side caching

This decision reduces later production surface area in a few places:

- no storefront dependency on Directus browser CORS for the main content path
- no requirement to ship a Directus public token to the browser for the main content path
- no nginx change required just because of this decision

If asset delivery is later proxied through the main domain, that would be a separate deployment decision.
