CREATE TABLE IF NOT EXISTS payment_refund (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id UUID NOT NULL REFERENCES payment(id),
    refund_id TEXT NOT NULL,
    refund_status TEXT,
    refund_amount BIGINT,
    refund_currency VARCHAR(3),
    refund_date TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_payment_refund_refund_id
    ON payment_refund(refund_id);

CREATE INDEX IF NOT EXISTS idx_payment_refund_payment_id
    ON payment_refund(payment_id);
