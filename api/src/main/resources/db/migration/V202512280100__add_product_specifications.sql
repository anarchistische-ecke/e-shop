ALTER TABLE product
    ADD COLUMN IF NOT EXISTS specifications TEXT;

COMMENT ON COLUMN product.specifications IS 'Serialized product technical specifications (JSON).';
