CREATE TABLE IF NOT EXISTS cart (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL REFERENCES customer(id) UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE cart IS 'Shopping cart for customers.';
COMMENT ON COLUMN cart.customer_id IS 'Owner of this cart.';
CREATE INDEX IF NOT EXISTS idx_cart_customer ON cart(customer_id);