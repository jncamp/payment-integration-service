ALTER TABLE payments
    ALTER COLUMN status TYPE VARCHAR(32);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS client_secret VARCHAR(120),
    ADD COLUMN IF NOT EXISTS latest_charge_id VARCHAR(120),
    ADD COLUMN IF NOT EXISTS metadata_json TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS ux_payment_client_secret
    ON payments(client_secret);
