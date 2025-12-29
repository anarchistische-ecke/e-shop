ALTER TABLE customer
    ADD COLUMN IF NOT EXISTS phone TEXT;

ALTER TABLE customer
    ADD COLUMN IF NOT EXISTS yandex_id TEXT;

ALTER TABLE customer
    ADD COLUMN IF NOT EXISTS vk_id TEXT;

ALTER TABLE customer
    ADD COLUMN IF NOT EXISTS password TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS idx_customer_phone_unique
    ON customer(phone)
    WHERE phone IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_customer_yandex_id_unique
    ON customer(yandex_id)
    WHERE yandex_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_customer_vk_id_unique
    ON customer(vk_id)
    WHERE vk_id IS NOT NULL;

COMMENT ON COLUMN customer.password IS 'BCrypt hashed password (nullable for social login users).';
COMMENT ON COLUMN customer.phone IS 'Customer phone number (unique when provided).';
COMMENT ON COLUMN customer.yandex_id IS 'Yandex social login id (unique when provided).';
COMMENT ON COLUMN customer.vk_id IS 'VK social login id (unique when provided).';
