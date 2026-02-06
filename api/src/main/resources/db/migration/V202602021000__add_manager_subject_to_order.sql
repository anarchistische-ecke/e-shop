ALTER TABLE customer_order
    ADD COLUMN IF NOT EXISTS manager_subject TEXT;

UPDATE customer_order
SET manager_subject = COALESCE(manager_subject, manager_id::text)
WHERE manager_subject IS NULL AND manager_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_customer_order_manager_subject
    ON customer_order(manager_subject);
