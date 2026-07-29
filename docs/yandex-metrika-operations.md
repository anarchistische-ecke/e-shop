# Yandex Metrica Conversion Operations

The storefront emits browser-side funnel events, while the backend outbox sends authoritative order and payment outcomes. Both layers are required: browser events describe customer behaviour, and the backend confirms payments even when the customer does not return from YooKassa.

## Production Configuration

Keep the OAuth token in the production secret environment, never in Git:

```env
YANDEX_METRIKA_ENABLED=true
YANDEX_METRIKA_COUNTER_ID=109831177
YANDEX_METRIKA_OAUTH_TOKEN=replace-me-metrika-oauth-token
YANDEX_METRIKA_OFFLINE_IMPORT_ENABLED=true
YANDEX_METRIKA_DISPATCHER_ENABLED=true
```

When offline import is enabled, the API refuses to start without both the counter ID and OAuth token. This prevents a deployment that silently accumulates conversions without sending them.

## Funnel Goals

Configure JavaScript-event goals in counter `109831177` for the storefront stages used by the client:

1. `product_detail`
2. `add_to_cart`
3. `view_cart`
4. `begin_checkout`
5. `checkout_submit`
6. `payment_session_success`
7. `purchase`

Configure `purchase_paid` as the authoritative offline conversion target. The backend also emits `order_created`, `order_cancelled`, and `order_refunded` for operational analysis.

Use `purchase_paid` for revenue and paid-order conversion. Treat the client-side `purchase` goal as a return-page signal, because a customer can finish payment without returning to the storefront.

## Verification

1. Deploy with all five production variables set.
2. Open Storefront Ops → Analytics.
3. Confirm Yandex Metrica reports `Offline import: Вкл`, `Counter: OK`, and `OAuth: OK`.
4. Confirm existing `Pending` rows begin moving to `Sent` after the dispatcher interval.
5. Complete one controlled payment and verify a `purchase_paid` conversion appears in Metrica with the same purchase/order attribution.

`Failed` rows indicate an upload or authorization error. Inspect the API warning for the root response, correct the secret or counter permission, and allow the normal retry schedule to resend the row.

## Dashboard Semantics

Storefront Ops deliberately exposes two different rates:

- `Заказ → оплата`: paid orders divided by created orders for the selected period.
- `Конверсия ссылок`: paid manager payment links divided by manager payment links sent.

Neither metric is the complete visitor funnel. Product-view through checkout behaviour remains in Yandex Metrica, where the goals above can be arranged as a funnel report.
