CREATE TABLE IF NOT EXISTS product_image (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    url TEXT NOT NULL,
    object_key TEXT NOT NULL UNIQUE,
    position INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE product_image IS 'Images attached to products and stored in object storage.';
CREATE INDEX IF NOT EXISTS idx_product_image_product ON product_image(product_id);
CREATE INDEX IF NOT EXISTS idx_product_image_product_position ON product_image(product_id, position);
