CREATE TABLE IF NOT EXISTS product_attribute (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES product(id),
    attribute_id UUID NOT NULL REFERENCES attribute_def(id),
    value TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (product_id, attribute_id)
);
COMMENT ON TABLE product_attribute IS 'Attribute values assigned to products.';
COMMENT ON COLUMN product_attribute.value IS 'Attribute value for the product.';
CREATE INDEX IF NOT EXISTS idx_product_attr_product ON product_attribute(product_id);