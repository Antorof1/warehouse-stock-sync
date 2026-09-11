CREATE TABLE movements (
    id UUID PRIMARY KEY,
    sku VARCHAR(64) NOT NULL,
    warehouse_id VARCHAR(64) NOT NULL,
    quantity_delta INTEGER NOT NULL,
    reason VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL
);