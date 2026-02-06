ALTER TABLE payment
    ADD COLUMN IF NOT EXISTS refund_id TEXT,
    ADD COLUMN IF NOT EXISTS refund_status TEXT,
    ADD COLUMN IF NOT EXISTS refund_amount BIGINT,
    ADD COLUMN IF NOT EXISTS refund_currency VARCHAR(3),
    ADD COLUMN IF NOT EXISTS refund_date TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_payment_refund_id
    ON payment(refund_id);
