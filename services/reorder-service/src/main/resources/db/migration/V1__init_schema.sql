CREATE TABLE reorder_rules (
    sku VARCHAR(64) NOT NULL,
    warehouse_id VARCHAR(64) NOT NULL,
    reorder_point INTEGER NOT NULL,
    reorder_up_to INTEGER NOT NULL,
    PRIMARY KEY (sku, warehouse_id)
);

CREATE TABLE purchase_orders (
    id UUID PRIMARY KEY,
    sku VARCHAR(64) NOT NULL,
    warehouse_id VARCHAR(64) NOT NULL,
    quantity INTEGER NOT NULL,
    received_quantity INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    topic VARCHAR(64) NOT NULL,
    key VARCHAR(64) NOT NULL,
    type VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_purchase_orders_open_per_sku_warehouse
    ON purchase_orders (sku, warehouse_id) WHERE status = 'OPEN';

CREATE TABLE processed_events (
    id UUID PRIMARY KEY
);

CREATE INDEX idx_outbox_pending ON outbox_events (created_at) WHERE published_at IS NULL;
CREATE INDEX idx_outbox_published ON outbox_events (published_at) WHERE published_at IS NOT NULL;
