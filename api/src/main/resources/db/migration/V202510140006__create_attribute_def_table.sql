CREATE TABLE IF NOT EXISTS attribute_def (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL UNIQUE,
    data_type TEXT NOT NULL,
    unit TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE attribute_def IS 'Definitions of custom product/variant attributes (e.g. color, size).';
COMMENT ON COLUMN attribute_def.name IS 'Attribute name (unique).';