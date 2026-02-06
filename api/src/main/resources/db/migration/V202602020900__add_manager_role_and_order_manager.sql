ALTER TABLE admin_user
    ADD COLUMN IF NOT EXISTS role VARCHAR(32) NOT NULL DEFAULT 'ADMIN';

UPDATE admin_user
SET role = 'ADMIN'
WHERE role IS NULL;

ALTER TABLE customer_order
    ADD COLUMN IF NOT EXISTS manager_id UUID;

CREATE INDEX IF NOT EXISTS idx_customer_order_manager_id
    ON customer_order(manager_id);
