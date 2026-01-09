ALTER TABLE customer_order
    ADD COLUMN IF NOT EXISTS public_token TEXT,
    ADD COLUMN IF NOT EXISTS receipt_email TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS idx_customer_order_public_token
    ON customer_order(public_token);

ALTER TABLE order_item
    ADD COLUMN IF NOT EXISTS product_name TEXT,
    ADD COLUMN IF NOT EXISTS variant_name TEXT,
    ADD COLUMN IF NOT EXISTS sku TEXT;

ALTER TABLE payment
    ADD COLUMN IF NOT EXISTS provider_payment_id TEXT,
    ADD COLUMN IF NOT EXISTS confirmation_url TEXT;

CREATE INDEX IF NOT EXISTS idx_payment_provider_payment_id
    ON payment(provider_payment_id);
