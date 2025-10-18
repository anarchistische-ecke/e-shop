CREATE TABLE IF NOT EXISTS brand (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL UNIQUE,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE brand IS 'Product brands or manufacturers.';
COMMENT ON COLUMN brand.name IS 'Brand name (unique).';
CREATE INDEX IF NOT EXISTS idx_brand_name ON brand(name);
