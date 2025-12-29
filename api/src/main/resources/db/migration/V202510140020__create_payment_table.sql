CREATE TABLE IF NOT EXISTS payment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES "order"(id),
    amount BIGINT NOT NULL,
    currency CHAR(3) NOT NULL,
    method TEXT NOT NULL,
    status TEXT NOT NULL,
    payment_date TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE payment IS 'Payments for orders.';
COMMENT ON COLUMN payment.method IS 'Payment method (e.g. Credit Card).';
COMMENT ON COLUMN payment.status IS 'Payment status.';
CREATE INDEX IF NOT EXISTS idx_payment_order ON payment(order_id);