CREATE TABLE IF NOT EXISTS inventory (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    variant_id UUID NOT NULL UNIQUE REFERENCES product_variant(id),
    quantity INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE inventory IS 'Inventory levels per product variant.';
COMMENT ON COLUMN inventory.quantity IS 'Available stock quantity.';
CREATE INDEX IF NOT EXISTS idx_inventory_variant ON inventory(variant_id);