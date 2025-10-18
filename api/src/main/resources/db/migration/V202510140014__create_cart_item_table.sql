CREATE TABLE IF NOT EXISTS cart_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cart_id UUID NOT NULL REFERENCES cart(id),
    variant_id UUID NOT NULL REFERENCES product_variant(id),
    quantity INT NOT NULL CHECK (quantity > 0),
    unit_price_amount BIGINT NOT NULL,
    unit_price_currency CHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (cart_id, variant_id)
);
COMMENT ON TABLE cart_item IS 'Items in a customer\'s shopping cart.';
COMMENT ON COLUMN cart_item.quantity IS 'Quantity of the variant in cart.';
COMMENT ON COLUMN cart_item.unit_price_amount IS 'Unit price in smallest currency unit.';
CREATE INDEX IF NOT EXISTS idx_cartitem_cart ON cart_item(cart_id);
CREATE INDEX IF NOT EXISTS idx_cartitem_variant ON cart_item(variant_id);