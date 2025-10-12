CREATE TABLE IF NOT EXISTS cart (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE IF NOT EXISTS cart_item (
    id UUID PRIMARY KEY,
    variant_id UUID NOT NULL,
    quantity INT NOT NULL,
    unit_price_amount BIGINT NOT NULL,
    unit_price_currency VARCHAR(3) NOT NULL,
    cart_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_cart FOREIGN KEY (cart_id) REFERENCES cart(id) ON DELETE CASCADE
);
