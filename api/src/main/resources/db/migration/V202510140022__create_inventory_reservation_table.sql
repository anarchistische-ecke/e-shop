CREATE TABLE IF NOT EXISTS inventory_reservation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_item_id UUID NOT NULL REFERENCES order_item(id),
    variant_id UUID NOT NULL REFERENCES product_variant(id),
    reserved_quantity INT NOT NULL,
    reserved_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    released_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE inventory_reservation IS 'Reserved stock for pending orders.';
COMMENT ON COLUMN inventory_reservation.reserved_quantity IS 'Quantity reserved for the order item.';
CREATE INDEX IF NOT EXISTS idx_invres_orderitem ON inventory_reservation(order_item_id);
CREATE INDEX IF NOT EXISTS idx_invres_variant ON inventory_reservation(variant_id);