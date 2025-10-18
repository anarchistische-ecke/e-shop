CREATE TABLE IF NOT EXISTS coupon_redemption (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    coupon_id UUID NOT NULL REFERENCES coupon(id),
    customer_id UUID REFERENCES customer(id),
    order_id UUID REFERENCES "order"(id),
    redeemed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE coupon_redemption IS 'Records of coupon usage by customers.';
CREATE INDEX IF NOT EXISTS idx_couponred_coup ON coupon_redemption(coupon_id);
CREATE INDEX IF NOT EXISTS idx_couponred_cust ON coupon_redemption(customer_id);