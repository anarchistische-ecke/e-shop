CREATE TABLE IF NOT EXISTS stock_adjustment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    variant_id UUID NOT NULL REFERENCES product_variant(id),
    delta_quantity INT NOT NULL,
    stock_after INT NOT NULL,
    idempotency_key TEXT NOT NULL UNIQUE,
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_stock_adjustment_variant ON stock_adjustment(variant_id);
CREATE INDEX IF NOT EXISTS idx_stock_adjustment_idempotency ON stock_adjustment(idempotency_key);
