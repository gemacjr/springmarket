CREATE TABLE inventory_items (
    product_id VARCHAR(36) PRIMARY KEY,
    available_qty INT NOT NULL CHECK (available_qty >= 0),
    reserved_qty INT NOT NULL CHECK (reserved_qty >= 0),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE orders (
    id UUID PRIMARY KEY,
    buyer_id UUID NOT NULL REFERENCES users(id),
    status VARCHAR(32) NOT NULL,
    total_amount NUMERIC(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id VARCHAR(36) NOT NULL,
    quantity INT NOT NULL,
    unit_price NUMERIC(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL
);
