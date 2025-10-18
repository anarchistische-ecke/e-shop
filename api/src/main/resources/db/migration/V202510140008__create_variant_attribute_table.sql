CREATE TABLE IF NOT EXISTS variant_attribute (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    variant_id UUID NOT NULL REFERENCES product_variant(id),
    attribute_id UUID NOT NULL REFERENCES attribute_def(id),
    value TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (variant_id, attribute_id)
);
COMMENT ON TABLE variant_attribute IS 'Attribute values assigned to product variants.';
COMMENT ON COLUMN variant_attribute.value IS 'Attribute value for the variant.';
CREATE INDEX IF NOT EXISTS idx_variant_attr_variant ON variant_attribute(variant_id);