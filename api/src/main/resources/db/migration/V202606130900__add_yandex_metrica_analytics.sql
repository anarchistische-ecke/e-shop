alter table customer_order
    add column if not exists metrika_client_id varchar(128),
    add column if not exists metrika_user_id varchar(128),
    add column if not exists yclid varchar(256),
    add column if not exists utm_source varchar(255),
    add column if not exists utm_medium varchar(255),
    add column if not exists utm_campaign varchar(255),
    add column if not exists utm_content varchar(255),
    add column if not exists utm_term varchar(255),
    add column if not exists utm_id varchar(255),
    add column if not exists analytics_landing_page varchar(1024),
    add column if not exists analytics_current_path varchar(1024),
    add column if not exists analytics_referrer varchar(1024),
    add column if not exists analytics_session_started_at timestamptz,
    add column if not exists analytics_purchase_id varchar(128),
    add column if not exists analytics_payload_json text;

create table if not exists metrika_outbox (
    id uuid primary key,
    event_key varchar(255) not null unique,
    event_type varchar(80) not null,
    order_id uuid,
    target varchar(128) not null,
    payload text not null,
    status varchar(40) not null,
    attempt_count integer not null default 0,
    next_attempt_at timestamptz not null default now(),
    last_error text,
    sent_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_metrika_outbox_status_next_attempt
    on metrika_outbox (status, next_attempt_at);

create index if not exists idx_metrika_outbox_order_id
    on metrika_outbox (order_id);
