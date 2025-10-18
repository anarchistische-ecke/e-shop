CREATE TABLE IF NOT EXISTS category (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    slug TEXT NOT NULL UNIQUE,
    parent_id UUID REFERENCES category(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE category IS 'Product categories (hierarchical).';
COMMENT ON COLUMN category.name IS 'Category name.';
COMMENT ON COLUMN category.slug IS 'Unique URL-friendly identifier.';
CREATE INDEX IF NOT EXISTS idx_category_parent ON category(parent_id);