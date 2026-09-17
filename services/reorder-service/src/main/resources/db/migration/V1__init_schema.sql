CREATE TABLE reorder_rules (
    sku VARCHAR(64) NOT NULL,
    warehouse_id VARCHAR(64) NOT NULL,
    reorder_point INTEGER NOT NULL,
    reorder_up_to INTEGER NOT NULL,
    PRIMARY KEY (sku, warehouse_id)
);

CREATE TABLE orders (
    id UUID PRIMARY KEY,
    sku VARCHAR(64) NOT NULL,
    warehouse_id VARCHAR(64) NOT NULL,
    quantity INTEGER NOT NULL,
    received_quantity INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL
);

CREATE UNIQUE INDEX uq_orders_open_per_sku_warehouse
    ON orders (sku, warehouse_id) WHERE status = 'OPEN';

CREATE TABLE processed_events (
    id UUID PRIMARY KEY
);
