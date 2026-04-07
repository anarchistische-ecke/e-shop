ALTER TABLE payment
    ADD COLUMN IF NOT EXISTS confirmation_type TEXT,
    ADD COLUMN IF NOT EXISTS confirmation_token TEXT;
