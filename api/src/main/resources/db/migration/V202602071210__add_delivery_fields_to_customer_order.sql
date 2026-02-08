ALTER TABLE customer_order
    ADD COLUMN IF NOT EXISTS delivery_amount BIGINT,
    ADD COLUMN IF NOT EXISTS delivery_currency VARCHAR(3),
    ADD COLUMN IF NOT EXISTS delivery_provider TEXT,
    ADD COLUMN IF NOT EXISTS delivery_method TEXT,
    ADD COLUMN IF NOT EXISTS delivery_address TEXT,
    ADD COLUMN IF NOT EXISTS delivery_pickup_point_id TEXT,
    ADD COLUMN IF NOT EXISTS delivery_pickup_point_name TEXT,
    ADD COLUMN IF NOT EXISTS delivery_interval_from TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS delivery_interval_to TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS delivery_offer_id TEXT,
    ADD COLUMN IF NOT EXISTS delivery_request_id TEXT,
    ADD COLUMN IF NOT EXISTS delivery_status TEXT;

CREATE INDEX IF NOT EXISTS idx_customer_order_delivery_request_id
    ON customer_order(delivery_request_id);
