ALTER TABLE customer_order
    ADD COLUMN IF NOT EXISTS archived_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS archived_by TEXT,
    ADD COLUMN IF NOT EXISTS archive_reason TEXT;

CREATE INDEX IF NOT EXISTS idx_customer_order_archived_at
    ON customer_order(archived_at);

CREATE INDEX IF NOT EXISTS idx_customer_order_active_order_date
    ON customer_order(order_date DESC)
    WHERE archived_at IS NULL;
