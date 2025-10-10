CREATE TABLE IF NOT EXISTS customer_order (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    order_date TIMESTAMPTZ NOT NULL,
    status VARCHAR(50) NOT NULL,
    payment_id UUID,
    shipment_id UUID,
    amount BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE IF NOT EXISTS order_item (
    id UUID PRIMARY KEY,
    variant_id UUID NOT NULL,
    quantity INT NOT NULL,
    unit_price_amount BIGINT NOT NULL,
    unit_price_currency VARCHAR(3) NOT NULL,
    order_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_order FOREIGN KEY (order_id) REFERENCES customer_order(id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS payment (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    amount BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    method VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL,
    payment_date TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_order_payment FOREIGN KEY (order_id) REFERENCES customer_order(id)
);
CREATE TABLE IF NOT EXISTS shipment (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    carrier VARCHAR(100) NOT NULL,
    tracking_number VARCHAR(100) NOT NULL,
    shipped_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_order_shipment FOREIGN KEY (order_id) REFERENCES customer_order(id)
);
