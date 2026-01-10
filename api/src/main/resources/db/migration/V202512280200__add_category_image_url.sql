ALTER TABLE category
    ADD COLUMN IF NOT EXISTS image_url TEXT;

COMMENT ON COLUMN category.image_url IS 'Public image URL for category tiles.';
