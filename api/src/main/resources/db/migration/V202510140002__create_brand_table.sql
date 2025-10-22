CREATE TABLE IF NOT EXISTS brand (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    slug TEXT NOT NULL UNIQUE,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE brand IS 'Product brands.';
COMMENT ON COLUMN brand.name IS 'Brand name.';
COMMENT ON COLUMN brand.slug IS 'Unique URL-friendly brand identifier.';
