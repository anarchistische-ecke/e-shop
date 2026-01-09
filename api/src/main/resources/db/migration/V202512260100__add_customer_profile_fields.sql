ALTER TABLE customer
    ADD COLUMN IF NOT EXISTS birth_date DATE;

ALTER TABLE customer
    ADD COLUMN IF NOT EXISTS gender TEXT;

ALTER TABLE customer
    ADD COLUMN IF NOT EXISTS marketing_opt_in BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN customer.birth_date IS 'Customer birth date.';
COMMENT ON COLUMN customer.gender IS 'Customer gender.';
COMMENT ON COLUMN customer.marketing_opt_in IS 'Customer marketing subscription opt-in.';
