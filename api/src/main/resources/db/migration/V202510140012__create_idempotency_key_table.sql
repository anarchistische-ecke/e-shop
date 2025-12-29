CREATE TABLE IF NOT EXISTS idempotency_key (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES customer(id),
    key_value TEXT NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE idempotency_key IS 'Idempotency keys to avoid duplicate transactions.';
COMMENT ON COLUMN idempotency_key.key_value IS 'Unique idempotency token for a request.';
CREATE INDEX IF NOT EXISTS idx_idempotency_user ON idempotency_key(user_id);