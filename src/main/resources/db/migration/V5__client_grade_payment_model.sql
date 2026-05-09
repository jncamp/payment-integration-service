CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS citext;



CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE IF NOT EXISTS api_clients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_code VARCHAR(50) NOT NULL UNIQUE,
    client_name VARCHAR(150) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    api_key_hash VARCHAR(255) NOT NULL UNIQUE,
    webhook_signing_key_hash VARCHAR(255),
    rate_limit_per_min INTEGER NOT NULL DEFAULT 120,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_api_clients_status CHECK (status IN ('ACTIVE','INACTIVE','SUSPENDED'))
);

CREATE TABLE IF NOT EXISTS customers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id UUID NOT NULL REFERENCES api_clients(id) ON DELETE RESTRICT,
    external_customer_id VARCHAR(120),
    email CITEXT NOT NULL,
    full_name VARCHAR(150),
    phone VARCHAR(40),
    default_currency CHAR(3) NOT NULL DEFAULT 'USD',
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_customers_client_external UNIQUE (client_id, external_customer_id),
    CONSTRAINT chk_customers_default_currency CHECK (default_currency = UPPER(default_currency))
);
CREATE INDEX IF NOT EXISTS idx_customers_client_id ON customers(client_id);
CREATE INDEX IF NOT EXISTS idx_customers_email ON customers(email);

CREATE TABLE IF NOT EXISTS payment_intents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id UUID NOT NULL REFERENCES api_clients(id) ON DELETE RESTRICT,
    customer_id UUID REFERENCES customers(id) ON DELETE SET NULL,
    internal_reference VARCHAR(50) NOT NULL UNIQUE,
    provider VARCHAR(20) NOT NULL,
    provider_payment_intent_id VARCHAR(120) UNIQUE,
    client_secret VARCHAR(255) UNIQUE,
    amount BIGINT NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'REQUIRES_PAYMENT_METHOD',
    description VARCHAR(255),
    statement_descriptor VARCHAR(22),
    capture_method VARCHAR(20) NOT NULL DEFAULT 'AUTOMATIC',
    confirmation_method VARCHAR(20) NOT NULL DEFAULT 'AUTOMATIC',
    latest_charge_id UUID,
    failure_code VARCHAR(80),
    failure_message VARCHAR(255),
    idempotency_key VARCHAR(120),
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    confirmed_at TIMESTAMPTZ,
    succeeded_at TIMESTAMPTZ,
    canceled_at TIMESTAMPTZ,
    CONSTRAINT chk_payment_intents_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_payment_intents_currency_upper CHECK (currency = UPPER(currency)),
    CONSTRAINT chk_payment_intents_capture_method CHECK (capture_method IN ('AUTOMATIC','MANUAL')),
    CONSTRAINT chk_payment_intents_confirmation_method CHECK (confirmation_method IN ('AUTOMATIC','MANUAL')),
    CONSTRAINT uq_payment_intents_client_idempotency UNIQUE (client_id, idempotency_key),
    CONSTRAINT chk_payment_intents_status CHECK (status IN ('REQUIRES_PAYMENT_METHOD','REQUIRES_CONFIRMATION','REQUIRES_ACTION','PROCESSING','SUCCEEDED','FAILED','CANCELED','PARTIALLY_REFUNDED','REFUNDED')),
    CONSTRAINT chk_payment_intents_provider CHECK (provider IN ('MOCK','STRIPE','PAYPAL','MANUAL'))
);
CREATE INDEX IF NOT EXISTS idx_payment_intents_client_id ON payment_intents(client_id);
CREATE INDEX IF NOT EXISTS idx_payment_intents_customer_id ON payment_intents(customer_id);
CREATE INDEX IF NOT EXISTS idx_payment_intents_status ON payment_intents(status);
CREATE INDEX IF NOT EXISTS idx_payment_intents_provider_status ON payment_intents(provider, status);
CREATE INDEX IF NOT EXISTS idx_payment_intents_created_at ON payment_intents(created_at DESC);

CREATE TABLE IF NOT EXISTS charges (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_intent_id UUID NOT NULL REFERENCES payment_intents(id) ON DELETE CASCADE,
    provider_charge_id VARCHAR(120) UNIQUE,
    amount_authorized BIGINT NOT NULL,
    amount_captured BIGINT NOT NULL DEFAULT 0,
    amount_refunded BIGINT NOT NULL DEFAULT 0,
    currency CHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    payment_method_type VARCHAR(50),
    card_brand VARCHAR(50),
    card_last4 VARCHAR(4),
    failure_code VARCHAR(80),
    failure_message VARCHAR(255),
    receipt_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    authorized_at TIMESTAMPTZ,
    captured_at TIMESTAMPTZ,
    CONSTRAINT chk_charges_amount_authorized_positive CHECK (amount_authorized > 0),
    CONSTRAINT chk_charges_amount_captured_nonnegative CHECK (amount_captured >= 0),
    CONSTRAINT chk_charges_amount_refunded_nonnegative CHECK (amount_refunded >= 0),
    CONSTRAINT chk_charges_currency_upper CHECK (currency = UPPER(currency)),
    CONSTRAINT chk_charges_captured_lte_authorized CHECK (amount_captured <= amount_authorized),
    CONSTRAINT chk_charges_refunded_lte_captured CHECK (amount_refunded <= amount_captured),
    CONSTRAINT chk_charges_status CHECK (status IN ('PENDING','AUTHORIZED','CAPTURED','FAILED','VOIDED','REFUNDED','PARTIALLY_REFUNDED'))
);
CREATE INDEX IF NOT EXISTS idx_charges_payment_intent_id ON charges(payment_intent_id);
CREATE INDEX IF NOT EXISTS idx_charges_status ON charges(status);
CREATE INDEX IF NOT EXISTS idx_charges_created_at ON charges(created_at DESC);

ALTER TABLE payment_intents
    ADD CONSTRAINT fk_payment_intents_latest_charge
    FOREIGN KEY (latest_charge_id) REFERENCES charges(id) ON DELETE SET NULL;

CREATE TABLE IF NOT EXISTS refunds (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_intent_id UUID NOT NULL REFERENCES payment_intents(id) ON DELETE CASCADE,
    charge_id UUID REFERENCES charges(id) ON DELETE SET NULL,
    internal_reference VARCHAR(50) NOT NULL UNIQUE,
    provider_refund_id VARCHAR(120) UNIQUE,
    amount BIGINT NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reason VARCHAR(80),
    failure_message VARCHAR(255),
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    succeeded_at TIMESTAMPTZ,
    CONSTRAINT chk_refunds_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_refunds_currency_upper CHECK (currency = UPPER(currency)),
    CONSTRAINT chk_refunds_reason CHECK (reason IS NULL OR reason IN ('requested_by_customer','duplicate','fraudulent','internal')),
    CONSTRAINT chk_refunds_status CHECK (status IN ('PENDING','SUCCEEDED','FAILED','CANCELED'))
);
CREATE INDEX IF NOT EXISTS idx_refunds_payment_intent_id ON refunds(payment_intent_id);
CREATE INDEX IF NOT EXISTS idx_refunds_charge_id ON refunds(charge_id);
CREATE INDEX IF NOT EXISTS idx_refunds_status ON refunds(status);
CREATE INDEX IF NOT EXISTS idx_refunds_created_at ON refunds(created_at DESC);

CREATE TABLE IF NOT EXISTS webhook_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id UUID REFERENCES api_clients(id) ON DELETE SET NULL,
    provider VARCHAR(20) NOT NULL,
    provider_event_id VARCHAR(150) NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    object_type VARCHAR(80),
    object_id VARCHAR(120),
    status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
    signature_valid BOOLEAN,
    http_headers_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    payload_json JSONB NOT NULL,
    processing_error TEXT,
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ,
    CONSTRAINT uq_webhook_provider_event UNIQUE (provider, provider_event_id),
    CONSTRAINT chk_webhook_events_status CHECK (status IN ('RECEIVED','PROCESSED','FAILED','IGNORED')),
    CONSTRAINT chk_webhook_events_provider CHECK (provider IN ('MOCK','STRIPE','PAYPAL','MANUAL'))
);
CREATE INDEX IF NOT EXISTS idx_webhook_events_status ON webhook_events(status);
CREATE INDEX IF NOT EXISTS idx_webhook_events_type ON webhook_events(event_type);
CREATE INDEX IF NOT EXISTS idx_webhook_events_received_at ON webhook_events(received_at DESC);

INSERT INTO api_clients (client_code, client_name, status, api_key_hash)
SELECT 'default', 'Default Demo Client', 'ACTIVE', 'legacy-single-api-key'
WHERE NOT EXISTS (SELECT 1 FROM api_clients WHERE client_code = 'default');

INSERT INTO customers (client_id, email, full_name, default_currency, metadata_json, created_at, updated_at)
SELECT DISTINCT ac.id, p.customer_email, p.customer_name, p.currency, '{}'::jsonb, COALESCE(p.created_at, NOW()), COALESCE(p.updated_at, NOW())
FROM payments p
JOIN api_clients ac ON ac.client_code = 'default'
WHERE NOT EXISTS (
  SELECT 1 FROM customers c
  WHERE c.client_id = ac.id AND lower(c.email::text) = lower(p.customer_email)
);

INSERT INTO payment_intents (
    id, client_id, customer_id, internal_reference, provider, provider_payment_intent_id, client_secret,
    amount, currency, status, latest_charge_id, failure_message, idempotency_key, metadata_json,
    created_at, updated_at, succeeded_at, canceled_at
)
SELECT
    p.id,
    ac.id,
    c.id,
    CONCAT('LEGACY-', REPLACE(p.id::text, '-', '')),
    CASE WHEN p.provider = 'STRIPE' THEN 'STRIPE' ELSE 'MOCK' END,
    p.provider_payment_id,
    p.client_secret,
    (p.amount * 100)::BIGINT,
    p.currency,
    CASE p.status
      WHEN 'REQUIRES_PAYMENT_METHOD' THEN 'REQUIRES_PAYMENT_METHOD'
      WHEN 'REQUIRES_CONFIRMATION' THEN 'REQUIRES_CONFIRMATION'
      WHEN 'REQUIRES_ACTION' THEN 'REQUIRES_ACTION'
      WHEN 'PROCESSING' THEN 'PROCESSING'
      WHEN 'SUCCEEDED' THEN 'SUCCEEDED'
      WHEN 'FAILED' THEN 'FAILED'
      WHEN 'CANCELED' THEN 'CANCELED'
      WHEN 'PARTIALLY_REFUNDED' THEN 'PARTIALLY_REFUNDED'
      WHEN 'REFUNDED' THEN 'REFUNDED'
      ELSE 'FAILED'
    END,
    NULL,
    p.failure_reason,
    p.idempotency_key,
    COALESCE(NULLIF(p.metadata_json, '')::jsonb, '{}'::jsonb),
    p.created_at,
    p.updated_at,
    CASE WHEN p.status IN ('SUCCEEDED','PARTIALLY_REFUNDED','REFUNDED') THEN p.updated_at ELSE NULL END,
    CASE WHEN p.status = 'CANCELED' THEN p.updated_at ELSE NULL END
FROM payments p
JOIN api_clients ac ON ac.client_code = 'default'
LEFT JOIN customers c ON c.client_id = ac.id AND lower(c.email::text) = lower(p.customer_email)
WHERE NOT EXISTS (SELECT 1 FROM payment_intents pi WHERE pi.id = p.id);

INSERT INTO charges (
    id, payment_intent_id, provider_charge_id, amount_authorized, amount_captured, amount_refunded, currency,
    status, failure_message, created_at, updated_at, authorized_at, captured_at
)
SELECT
    gen_random_uuid(),
    pi.id,
    p.latest_charge_id,
    pi.amount,
    CASE WHEN pi.status IN ('SUCCEEDED','PARTIALLY_REFUNDED','REFUNDED') THEN pi.amount ELSE 0 END,
    COALESCE(p.refunded_amount, 0),
    pi.currency,
    CASE
      WHEN pi.status IN ('SUCCEEDED','PARTIALLY_REFUNDED','REFUNDED') THEN 'CAPTURED'
      WHEN pi.status = 'FAILED' THEN 'FAILED'
      ELSE 'PENDING'
    END,
    p.failure_reason,
    p.created_at,
    p.updated_at,
    p.created_at,
    CASE WHEN pi.status IN ('SUCCEEDED','PARTIALLY_REFUNDED','REFUNDED') THEN p.updated_at ELSE NULL END
FROM payments p
JOIN payment_intents pi ON pi.id = p.id
WHERE p.latest_charge_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM charges ch WHERE ch.payment_intent_id = pi.id);

UPDATE payment_intents pi
SET latest_charge_id = ch.id
FROM charges ch
WHERE ch.payment_intent_id = pi.id
  AND pi.latest_charge_id IS NULL;

INSERT INTO refunds (
    payment_intent_id, charge_id, internal_reference, provider_refund_id, amount, currency, status, reason, created_at, updated_at, succeeded_at
)
SELECT
    pi.id,
    pi.latest_charge_id,
    CONCAT('LEGACY-REF-', REPLACE(pi.id::text, '-', '')),
    p.provider_refund_id,
    p.refunded_amount,
    pi.currency,
    'SUCCEEDED',
    'requested_by_customer',
    p.updated_at,
    p.updated_at,
    p.updated_at
FROM payments p
JOIN payment_intents pi ON pi.id = p.id
WHERE COALESCE(p.refunded_amount, 0) > 0
  AND NOT EXISTS (SELECT 1 FROM refunds r WHERE r.payment_intent_id = pi.id);

CREATE TRIGGER trg_api_clients_updated_at BEFORE UPDATE ON api_clients FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_customers_updated_at BEFORE UPDATE ON customers FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_payment_intents_updated_at BEFORE UPDATE ON payment_intents FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_charges_updated_at BEFORE UPDATE ON charges FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_refunds_updated_at BEFORE UPDATE ON refunds FOR EACH ROW EXECUTE FUNCTION set_updated_at();
