ALTER TABLE product_image
    ADD COLUMN IF NOT EXISTS variant_id UUID NULL REFERENCES product_variant(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_product_image_variant ON product_image(variant_id);
