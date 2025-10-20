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

CREATE TABLE IF NOT EXISTS product_variant (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sku TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    product_id UUID NOT NULL REFERENCES product(id),
    stock_quantity INT NOT NULL DEFAULT 0,
    price_amount BIGINT NOT NULL,
    price_currency CHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE product_variant IS 'Specific variations (e.g. size/color) of a product.';
COMMENT ON COLUMN product_variant.sku IS 'Unique stock-keeping unit.';
COMMENT ON COLUMN product_variant.price_amount IS 'Price amount in smallest currency unit (e.g. kopecks).';
COMMENT ON COLUMN product_variant.price_currency IS 'ISO currency code for price.';
CREATE INDEX IF NOT EXISTS idx_variant_product ON product_variant(product_id);