CREATE TABLE IF NOT EXISTS shipment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES "order"(id),
    carrier TEXT NOT NULL,
    tracking_number TEXT UNIQUE NOT NULL,
    shipped_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE shipment IS 'Shipment details for an order.';
COMMENT ON COLUMN shipment.carrier IS 'Shipping carrier or service.';
COMMENT ON COLUMN shipment.tracking_number IS 'Unique tracking code.';
CREATE INDEX IF NOT EXISTS idx_shipment_order ON shipment(order_id);