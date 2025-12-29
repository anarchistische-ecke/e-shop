CREATE TABLE IF NOT EXISTS "order" (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL REFERENCES customer(id),
    order_date TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    status TEXT NOT NULL,
    total_amount BIGINT NOT NULL,
    total_currency CHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE "order" IS 'Customer orders.';
COMMENT ON COLUMN "order".status IS 'Order status (e.g. PENDING, PAID).';
CREATE INDEX IF NOT EXISTS idx_order_customer ON "order"(customer_id);
CREATE INDEX IF NOT EXISTS idx_order_date ON "order"(order_date);

CREATE TABLE IF NOT EXISTS order_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES "order"(id),
    variant_id UUID NOT NULL REFERENCES product_variant(id),
    product_name TEXT,
    variant_name TEXT,
    quantity INT NOT NULL CHECK (quantity > 0),
    unit_price_amount BIGINT NOT NULL,
    unit_price_currency CHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (order_id, variant_id)
);
COMMENT ON TABLE order_item IS 'Line items within an order.';
COMMENT ON COLUMN order_item.quantity IS 'Quantity of the variant ordered.';
COMMENT ON COLUMN order_item.unit_price_amount IS 'Unit price at order time.';
CREATE INDEX IF NOT EXISTS idx_orderitem_order ON order_item(order_id);
CREATE INDEX IF NOT EXISTS idx_orderitem_variant ON order_item(variant_id);