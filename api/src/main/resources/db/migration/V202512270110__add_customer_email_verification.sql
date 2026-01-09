ALTER TABLE customer
    ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS email_verified_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS email_confirmation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID,
    email TEXT NOT NULL,
    code TEXT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_email_confirmation_customer
        FOREIGN KEY (customer_id) REFERENCES customer(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_email_confirmation_email ON email_confirmation(email);
CREATE INDEX IF NOT EXISTS idx_email_confirmation_code ON email_confirmation(code);
