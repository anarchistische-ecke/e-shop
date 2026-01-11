ALTER TABLE product
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN product.is_active IS 'Whether the product is visible on the storefront.';
