CREATE TABLE payments (
    id UUID PRIMARY KEY,
    amount NUMERIC(12,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    customer_email VARCHAR(120) NOT NULL,
    customer_name VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL,
    provider VARCHAR(20) NOT NULL,
    provider_payment_id VARCHAR(120) UNIQUE,
    provider_refund_id VARCHAR(120),
    failure_reason VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
