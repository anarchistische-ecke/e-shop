CREATE TABLE IF NOT EXISTS product (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    description TEXT,
    slug TEXT NOT NULL UNIQUE,
    category_id UUID REFERENCES category(id),
    brand_id UUID REFERENCES brand(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE product IS 'Products offered for sale.';
COMMENT ON COLUMN product.name IS 'Product name.';
COMMENT ON COLUMN product.slug IS 'Unique URL-friendly product identifier.';
CREATE INDEX IF NOT EXISTS idx_product_category ON product(category_id);
CREATE INDEX IF NOT EXISTS idx_product_brand ON product(brand_id);