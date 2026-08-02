CREATE TABLE IF NOT EXISTS marketing_subscription (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    email TEXT NOT NULL,
    status TEXT NOT NULL,
    consent_version TEXT NOT NULL,
    source TEXT,
    confirmation_token_hash TEXT,
    unsubscribe_token_hash TEXT,
    requested_at TIMESTAMPTZ,
    confirmation_expires_at TIMESTAMPTZ,
    confirmed_at TIMESTAMPTZ,
    unsubscribed_at TIMESTAMPTZ,
    CONSTRAINT uk_marketing_subscription_email UNIQUE (email),
    CONSTRAINT uk_marketing_subscription_confirmation_token UNIQUE (confirmation_token_hash),
    CONSTRAINT uk_marketing_subscription_unsubscribe_token UNIQUE (unsubscribe_token_hash),
    CONSTRAINT ck_marketing_subscription_status
        CHECK (status IN ('PENDING', 'ACTIVE', 'UNSUBSCRIBED'))
);

CREATE INDEX IF NOT EXISTS idx_marketing_subscription_status
    ON marketing_subscription(status, updated_at DESC);

CREATE OR REPLACE VIEW marketing_subscription_readonly AS
SELECT
    id,
    email,
    status,
    consent_version,
    source,
    requested_at,
    confirmation_expires_at,
    confirmed_at,
    unsubscribed_at,
    created_at,
    updated_at
FROM marketing_subscription;
