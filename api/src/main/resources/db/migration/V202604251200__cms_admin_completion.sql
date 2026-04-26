CREATE TABLE IF NOT EXISTS order_status_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES customer_order(id) ON DELETE CASCADE,
    previous_status TEXT,
    next_status TEXT NOT NULL,
    actor TEXT,
    actor_role TEXT,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_order_status_history_order ON order_status_history(order_id);
CREATE INDEX IF NOT EXISTS idx_order_status_history_created ON order_status_history(created_at);

CREATE TABLE IF NOT EXISTS catalogue_import_job (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_name TEXT,
    status VARCHAR(40) NOT NULL,
    mode VARCHAR(40) NOT NULL DEFAULT 'CATALOGUE_STOCK',
    total_rows INT NOT NULL DEFAULT 0,
    valid_rows INT NOT NULL DEFAULT 0,
    invalid_rows INT NOT NULL DEFAULT 0,
    created_by TEXT,
    committed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_catalogue_import_job_created ON catalogue_import_job(created_at);

CREATE TABLE IF NOT EXISTS catalogue_import_row (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID NOT NULL REFERENCES catalogue_import_job(id) ON DELETE CASCADE,
    row_number INT NOT NULL,
    raw_data TEXT,
    sku TEXT,
    product_name TEXT,
    product_slug TEXT,
    variant_name TEXT,
    brand_slug TEXT,
    category_slug TEXT,
    price_amount BIGINT,
    currency CHAR(3),
    stock_quantity INT,
    valid BOOLEAN NOT NULL DEFAULT false,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_catalogue_import_row_job ON catalogue_import_row(job_id);
CREATE INDEX IF NOT EXISTS idx_catalogue_import_row_sku ON catalogue_import_row(sku);

CREATE TABLE IF NOT EXISTS promotion (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    type VARCHAR(40) NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    starts_at TIMESTAMPTZ,
    ends_at TIMESTAMPTZ,
    discount_percent INT,
    discount_amount BIGINT,
    sale_price_amount BIGINT,
    currency CHAR(3) NOT NULL DEFAULT 'RUB',
    threshold_amount BIGINT,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_promotion_status_dates ON promotion(status, starts_at, ends_at);
CREATE INDEX IF NOT EXISTS idx_promotion_type ON promotion(type);

CREATE TABLE IF NOT EXISTS promotion_target (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    promotion_id UUID NOT NULL REFERENCES promotion(id) ON DELETE CASCADE,
    target_kind VARCHAR(40) NOT NULL,
    target_key TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_promotion_target_promotion ON promotion_target(promotion_id);
CREATE INDEX IF NOT EXISTS idx_promotion_target_lookup ON promotion_target(target_kind, target_key);

CREATE TABLE IF NOT EXISTS promo_code (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code TEXT NOT NULL UNIQUE,
    status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    discount_percent INT,
    discount_amount BIGINT,
    threshold_amount BIGINT,
    starts_at TIMESTAMPTZ,
    ends_at TIMESTAMPTZ,
    max_redemptions INT,
    redemption_count INT NOT NULL DEFAULT 0,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_promo_code_status_dates ON promo_code(status, starts_at, ends_at);

ALTER TABLE cart
    ADD COLUMN IF NOT EXISTS promo_code TEXT;

CREATE TABLE IF NOT EXISTS tax_configuration (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    tax_system_code INT NOT NULL,
    vat_code INT NOT NULL,
    vat_rate_percent NUMERIC(6, 3),
    is_active BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_tax_configuration_single_active
    ON tax_configuration(is_active)
    WHERE is_active;

CREATE TABLE IF NOT EXISTS manager_payment_link (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES customer_order(id) ON DELETE CASCADE,
    manager_subject TEXT,
    manager_email TEXT,
    public_token TEXT,
    sent_at TIMESTAMPTZ,
    paid_at TIMESTAMPTZ,
    status VARCHAR(40) NOT NULL DEFAULT 'SENT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_manager_payment_link_manager ON manager_payment_link(manager_subject);
CREATE INDEX IF NOT EXISTS idx_manager_payment_link_order ON manager_payment_link(order_id);
CREATE INDEX IF NOT EXISTS idx_manager_payment_link_created ON manager_payment_link(created_at);

CREATE TABLE IF NOT EXISTS stock_alert_settings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    threshold INT NOT NULL DEFAULT 5,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO stock_alert_settings (id, threshold)
SELECT '00000000-0000-4000-8000-000000000511'::uuid, 5
WHERE NOT EXISTS (SELECT 1 FROM stock_alert_settings);
