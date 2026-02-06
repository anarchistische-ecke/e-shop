CREATE TABLE IF NOT EXISTS saved_payment_method (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL,
    provider_payment_method_id TEXT NOT NULL,
    method_type TEXT,
    method_status TEXT,
    title TEXT,
    card_last4 TEXT,
    card_first6 TEXT,
    card_type TEXT,
    card_expiry_month TEXT,
    card_expiry_year TEXT,
    card_issuer TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_saved_payment_method_provider_id
    ON saved_payment_method(provider_payment_method_id);

CREATE INDEX IF NOT EXISTS idx_saved_payment_method_customer
    ON saved_payment_method(customer_id);
