ALTER TABLE customer_order
    ADD COLUMN IF NOT EXISTS payment_id UUID,
    ADD COLUMN IF NOT EXISTS shipment_id UUID;
