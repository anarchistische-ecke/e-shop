CREATE TABLE IF NOT EXISTS coupon (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code TEXT NOT NULL UNIQUE,
    description TEXT,
    discount_type TEXT NOT NULL,
    discount_value BIGINT NOT NULL,
    currency CHAR(3),
    valid_from TIMESTAMPTZ,
    valid_until TIMESTAMPTZ,
    usage_limit INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE coupon IS 'Discount coupons for promotions.';
COMMENT ON COLUMN coupon.code IS 'Unique coupon code.';
COMMENT ON COLUMN coupon.discount_type IS 'Type of discount (e.g. percent or fixed).';