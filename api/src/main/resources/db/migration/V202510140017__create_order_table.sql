CREATE TABLE IF NOT EXISTS "order" (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL REFERENCES customer(id),
    order_date TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    status TEXT NOT NULL,
    total_amount BIGINT NOT NULL,
    total_currency CHAR(3) NOT NULL,
    shipping_address_id UUID REFERENCES address_book(id),
    billing_address_id UUID REFERENCES address_book(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE "order" IS 'Customer orders.';
COMMENT ON COLUMN "order".status IS 'Order status (e.g. PENDING, PAID).';
CREATE INDEX IF NOT EXISTS idx_order_customer ON "order"(customer_id);
CREATE INDEX IF NOT EXISTS idx_order_date ON "order"(order_date);