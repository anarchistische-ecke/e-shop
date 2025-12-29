CREATE TABLE IF NOT EXISTS product_category (
    product_id UUID NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES category(id) ON DELETE CASCADE,
    PRIMARY KEY (product_id, category_id)
);
COMMENT ON TABLE product_category IS 'Join table for products and categories (many-to-many).';
CREATE INDEX IF NOT EXISTS idx_product_category_product ON product_category(product_id);
CREATE INDEX IF NOT EXISTS idx_product_category_category ON product_category(category_id);

INSERT INTO product_category (product_id, category_id)
SELECT id, category_id
FROM product
WHERE category_id IS NOT NULL
ON CONFLICT DO NOTHING;
