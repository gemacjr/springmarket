CREATE TABLE payments (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id),
    status VARCHAR(32) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL
);
