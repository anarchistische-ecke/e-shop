ALTER TABLE payment
    ADD COLUMN IF NOT EXISTS receipt_registration TEXT,
    ADD COLUMN IF NOT EXISTS receipt_url TEXT;

UPDATE customer_order
SET status = 'RECEIVED'
WHERE status = 'COMPLETED';

CREATE TABLE IF NOT EXISTS notification_outbox (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    event_key TEXT NOT NULL,
    type TEXT NOT NULL,
    aggregate_type TEXT,
    aggregate_id UUID,
    recipient TEXT,
    payload TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_error TEXT,
    sent_at TIMESTAMPTZ,
    CONSTRAINT uk_notification_outbox_event_key UNIQUE (event_key)
);

CREATE INDEX IF NOT EXISTS idx_notification_outbox_due
    ON notification_outbox(status, next_attempt_at, created_at);

CREATE INDEX IF NOT EXISTS idx_notification_outbox_aggregate
    ON notification_outbox(aggregate_type, aggregate_id);

CREATE TABLE IF NOT EXISTS rma_request (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    rma_number TEXT NOT NULL,
    order_id UUID NOT NULL REFERENCES customer_order(id),
    customer_email TEXT,
    status TEXT NOT NULL DEFAULT 'REQUESTED',
    reason TEXT,
    desired_resolution TEXT,
    manager_comment TEXT,
    decided_by TEXT,
    decided_at TIMESTAMPTZ,
    decision_version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uk_rma_request_rma_number UNIQUE (rma_number)
);

CREATE INDEX IF NOT EXISTS idx_rma_request_order_id
    ON rma_request(order_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_rma_request_status
    ON rma_request(status, created_at DESC);
