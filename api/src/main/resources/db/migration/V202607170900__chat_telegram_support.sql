CREATE TABLE IF NOT EXISTS chat_conversation (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    customer_token_hash TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'OPEN',
    customer_name TEXT,
    customer_contact TEXT,
    customer_subject TEXT,
    customer_email TEXT,
    page_url TEXT,
    user_agent TEXT,
    last_message_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    CONSTRAINT uk_chat_conversation_token_hash UNIQUE (customer_token_hash)
);

CREATE INDEX IF NOT EXISTS idx_chat_conversation_status_last_message
    ON chat_conversation(status, last_message_at DESC);

CREATE TABLE IF NOT EXISTS chat_message (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    conversation_id UUID NOT NULL REFERENCES chat_conversation(id) ON DELETE CASCADE,
    sender TEXT NOT NULL,
    sender_label TEXT,
    body TEXT NOT NULL,
    source TEXT NOT NULL DEFAULT 'WEB'
);

CREATE INDEX IF NOT EXISTS idx_chat_message_conversation_created
    ON chat_message(conversation_id, created_at ASC, id ASC);

CREATE TABLE IF NOT EXISTS chat_telegram_message_map (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    conversation_id UUID NOT NULL REFERENCES chat_conversation(id) ON DELETE CASCADE,
    message_id UUID REFERENCES chat_message(id) ON DELETE SET NULL,
    telegram_chat_id BIGINT NOT NULL,
    telegram_message_id BIGINT NOT NULL,
    CONSTRAINT uk_chat_telegram_message UNIQUE (telegram_chat_id, telegram_message_id)
);

CREATE INDEX IF NOT EXISTS idx_chat_telegram_message_conversation
    ON chat_telegram_message_map(conversation_id, created_at DESC);

CREATE TABLE IF NOT EXISTS chat_telegram_update (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    update_id BIGINT NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_chat_telegram_update UNIQUE (update_id)
);

CREATE TABLE IF NOT EXISTS chat_delivery_outbox (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    conversation_id UUID NOT NULL REFERENCES chat_conversation(id) ON DELETE CASCADE,
    message_id UUID NOT NULL REFERENCES chat_message(id) ON DELETE CASCADE,
    status TEXT NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_error TEXT,
    sent_at TIMESTAMPTZ,
    telegram_message_id BIGINT,
    CONSTRAINT uk_chat_delivery_message UNIQUE (message_id)
);

CREATE INDEX IF NOT EXISTS idx_chat_delivery_due
    ON chat_delivery_outbox(status, next_attempt_at, created_at);
