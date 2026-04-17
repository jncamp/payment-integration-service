ALTER TABLE refunds
ALTER COLUMN metadata_json TYPE TEXT USING metadata_json::text;
      