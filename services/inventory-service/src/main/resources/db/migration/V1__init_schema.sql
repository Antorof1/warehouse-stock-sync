CREATE TABLE stock (
    sku VARCHAR(64) NOT NULL,
    warehouse_id VARCHAR(64) NOT NULL,
    quantity INTEGER NOT NULL,
    PRIMARY KEY (sku, warehouse_id)
);

CREATE TABLE processed_events (
    id UUID PRIMARY KEY
);
