CREATE TABLE IF NOT EXISTS address_book (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL REFERENCES customer(id),
    label TEXT,
    street TEXT NOT NULL,
    city TEXT NOT NULL,
    state TEXT,
    postal_code TEXT NOT NULL,
    country TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE address_book IS 'Stored addresses for customers (shipping/billing).';
COMMENT ON COLUMN address_book.label IS 'Address label (e.g. Home, Work).';
CREATE INDEX IF NOT EXISTS idx_address_customer ON address_book(customer_id);