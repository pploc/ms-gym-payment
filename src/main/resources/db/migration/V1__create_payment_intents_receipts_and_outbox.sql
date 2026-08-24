CREATE TABLE payment_intents (
    id VARCHAR(128) PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL,
    gym_id VARCHAR(128) NOT NULL,
    reference_id VARCHAR(128) NOT NULL,
    payment_type VARCHAR(64) NOT NULL CHECK (payment_type = 'PAYMENT_TYPE_MEMBERSHIP'),
    provider VARCHAR(64) NOT NULL CHECK (provider = 'SEPAY'),
    status VARCHAR(64) NOT NULL CHECK (status IN ('PAYMENT_STATUS_PENDING', 'PAYMENT_STATUS_COMPLETED')),
    intent_amount_vnd BIGINT NOT NULL CHECK (intent_amount_vnd >= 0),
    received_amount_vnd BIGINT,
    payment_code VARCHAR(64) NOT NULL UNIQUE,
    payment_url TEXT NOT NULL,
    provider_transaction_id BIGINT UNIQUE,
    provider_reference_code VARCHAR(255),
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_payment_intent_reference UNIQUE (payment_type, reference_id),
    CONSTRAINT completed_payment_fields CHECK (
        (status = 'PAYMENT_STATUS_PENDING' AND received_amount_vnd IS NULL AND provider_transaction_id IS NULL AND completed_at IS NULL)
        OR (status = 'PAYMENT_STATUS_COMPLETED' AND received_amount_vnd >= intent_amount_vnd
            AND provider_transaction_id IS NOT NULL AND completed_at IS NOT NULL)
    )
);

CREATE TABLE sepay_webhook_receipts (
    provider_transaction_id BIGINT PRIMARY KEY,
    payment_id VARCHAR(128),
    payment_code VARCHAR(64),
    transfer_type VARCHAR(8) NOT NULL,
    transfer_amount_vnd BIGINT NOT NULL CHECK (transfer_amount_vnd > 0),
    reference_code VARCHAR(255),
    payload_sha256 VARCHAR(64) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    kafka_key VARCHAR(255),
    event_type VARCHAR(100) NOT NULL,
    dedupe_key VARCHAR(255) UNIQUE,
    payload_type VARCHAR(255),
    topic VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    last_error TEXT,
    lease_owner VARCHAR(255),
    lease_expiration TIMESTAMPTZ
);

CREATE INDEX idx_outbox_ready
    ON outbox_events (status, next_attempt_at, created_at, id)
    WHERE status IN ('PENDING', 'IN_FLIGHT');
