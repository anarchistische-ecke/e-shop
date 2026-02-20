CREATE TABLE IF NOT EXISTS order_checkout_attempt (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key_value TEXT NOT NULL UNIQUE,
    request_hash TEXT NOT NULL,
    order_id UUID REFERENCES customer_order(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_order_checkout_attempt_order_id
    ON order_checkout_attempt(order_id);
