CREATE TABLE IF NOT EXISTS payment_refund_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id UUID NOT NULL REFERENCES payment(id) ON DELETE CASCADE,
    refund_id TEXT NOT NULL,
    order_item_id UUID NOT NULL REFERENCES order_item(id) ON DELETE CASCADE,
    quantity INT NOT NULL,
    refund_amount BIGINT NOT NULL,
    refund_currency VARCHAR(3) NOT NULL,
    refund_status TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_payment_refund_item_payment
    ON payment_refund_item(payment_id);

CREATE INDEX IF NOT EXISTS idx_payment_refund_item_order_item
    ON payment_refund_item(order_item_id);

CREATE UNIQUE INDEX IF NOT EXISTS idx_payment_refund_item_refund_order_item
    ON payment_refund_item(refund_id, order_item_id);
