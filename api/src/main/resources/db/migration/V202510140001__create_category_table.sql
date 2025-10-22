CREATE TABLE IF NOT EXISTS category (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id UUID REFERENCES category(id),
    slug TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    description TEXT,
    position INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    full_path TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE category IS 'Product categories (hierarchical).';
COMMENT ON COLUMN category.name IS 'Category name.';
COMMENT ON COLUMN category.slug IS 'Unique URL-friendly category identifier.';
COMMENT ON COLUMN category.position IS 'Position among siblings for ordering.';
COMMENT ON COLUMN category.is_active IS 'Whether the category is active.';
COMMENT ON COLUMN category.full_path IS 'Full path of category (e.g. electronics/phones).';
CREATE INDEX IF NOT EXISTS idx_category_parent ON category(parent_id);
