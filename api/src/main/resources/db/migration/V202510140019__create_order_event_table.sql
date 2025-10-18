REATE TABLE IF NOT EXISTS order_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES "order"(id),
    event_type TEXT NOT NULL,
    event_data TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE order_event IS 'History of status changes or events for an order.';
COMMENT ON COLUMN order_event.event_type IS 'Type of order event.';
CREATE INDEX IF NOT EXISTS idx_orderevent_order ON order_event(order_id);