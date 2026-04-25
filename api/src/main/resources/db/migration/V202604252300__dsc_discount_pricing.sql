ALTER TABLE customer_order
    ADD COLUMN IF NOT EXISTS original_subtotal_amount BIGINT,
    ADD COLUMN IF NOT EXISTS original_subtotal_currency CHAR(3),
    ADD COLUMN IF NOT EXISTS sale_subtotal_amount BIGINT,
    ADD COLUMN IF NOT EXISTS sale_subtotal_currency CHAR(3),
    ADD COLUMN IF NOT EXISTS eligible_discount_subtotal_amount BIGINT,
    ADD COLUMN IF NOT EXISTS eligible_discount_subtotal_currency CHAR(3),
    ADD COLUMN IF NOT EXISTS product_sale_discount_amount BIGINT,
    ADD COLUMN IF NOT EXISTS product_sale_discount_currency CHAR(3),
    ADD COLUMN IF NOT EXISTS cart_discount_amount BIGINT,
    ADD COLUMN IF NOT EXISTS cart_discount_currency CHAR(3),
    ADD COLUMN IF NOT EXISTS promo_code_discount_amount BIGINT,
    ADD COLUMN IF NOT EXISTS promo_code_discount_currency CHAR(3),
    ADD COLUMN IF NOT EXISTS total_discount_amount BIGINT,
    ADD COLUMN IF NOT EXISTS total_discount_currency CHAR(3),
    ADD COLUMN IF NOT EXISTS promo_code TEXT,
    ADD COLUMN IF NOT EXISTS promo_code_redemption_recorded BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS applied_cart_discount_type TEXT,
    ADD COLUMN IF NOT EXISTS applied_cart_discount_label TEXT,
    ADD COLUMN IF NOT EXISTS discount_summary TEXT;

ALTER TABLE order_item
    ADD COLUMN IF NOT EXISTS original_unit_price_amount BIGINT,
    ADD COLUMN IF NOT EXISTS original_unit_price_currency CHAR(3),
    ADD COLUMN IF NOT EXISTS product_sale_discount_amount BIGINT,
    ADD COLUMN IF NOT EXISTS product_sale_discount_currency CHAR(3),
    ADD COLUMN IF NOT EXISTS cart_discount_amount BIGINT,
    ADD COLUMN IF NOT EXISTS cart_discount_currency CHAR(3),
    ADD COLUMN IF NOT EXISTS payable_amount BIGINT,
    ADD COLUMN IF NOT EXISTS payable_currency CHAR(3),
    ADD COLUMN IF NOT EXISTS sale_promotion_id UUID,
    ADD COLUMN IF NOT EXISTS sale_promotion_name TEXT;

CREATE INDEX IF NOT EXISTS idx_customer_order_promo_code ON customer_order(promo_code)
    WHERE promo_code IS NOT NULL;
