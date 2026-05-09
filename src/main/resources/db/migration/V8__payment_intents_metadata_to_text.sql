ALTER TABLE payment_intents
ALTER COLUMN metadata_json TYPE TEXT USING metadata_json::text;
