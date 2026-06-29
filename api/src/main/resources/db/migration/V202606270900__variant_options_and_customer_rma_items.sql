ALTER TABLE product_variant
    ADD COLUMN IF NOT EXISTS color_code TEXT,
    ADD COLUMN IF NOT EXISTS color_label TEXT,
    ADD COLUMN IF NOT EXISTS color_hex TEXT,
    ADD COLUMN IF NOT EXISTS size_code TEXT,
    ADD COLUMN IF NOT EXISTS size_label TEXT,
    ADD COLUMN IF NOT EXISTS sort_order INT;

CREATE INDEX IF NOT EXISTS idx_product_variant_color_size
    ON product_variant(product_id, color_code, size_code);

CREATE TABLE IF NOT EXISTS rma_request_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rma_request_id UUID NOT NULL REFERENCES rma_request(id) ON DELETE CASCADE,
    order_item_id UUID NOT NULL REFERENCES order_item(id) ON DELETE CASCADE,
    quantity INT NOT NULL CHECK (quantity > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_rma_request_item_order_item UNIQUE (rma_request_id, order_item_id)
);

CREATE INDEX IF NOT EXISTS idx_rma_request_item_request
    ON rma_request_item(rma_request_id);

CREATE INDEX IF NOT EXISTS idx_rma_request_item_order_item
    ON rma_request_item(order_item_id);
