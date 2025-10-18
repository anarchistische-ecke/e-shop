CREATE TABLE IF NOT EXISTS media_asset (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    url TEXT NOT NULL,
    media_type TEXT NOT NULL,
    alt_text TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE media_asset IS 'Media files (e.g. product images).';
COMMENT ON COLUMN media_asset.url IS 'Location or URL of the asset.';