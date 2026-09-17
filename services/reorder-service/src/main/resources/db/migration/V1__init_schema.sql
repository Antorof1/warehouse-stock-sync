CREATE TABLE reorder_rules (
    sku VARCHAR(64) NOT NULL,
    warehouse_id VARCHAR(64) NOT NULL,
    reorder_point INTEGER NOT NULL,
    reorder_up_to INTEGER NOT NULL,
    pending_quantity INTEGER NOT NULL,
    PRIMARY KEY (sku, warehouse_id)
);

CREATE TABLE processed_events (
    id UUID PRIMARY KEY
);
