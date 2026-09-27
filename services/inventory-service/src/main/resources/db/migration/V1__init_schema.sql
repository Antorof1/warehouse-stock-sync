CREATE TABLE stock (
    sku VARCHAR(64) NOT NULL,
    warehouse_id VARCHAR(64) NOT NULL,
    quantity INTEGER NOT NULL,
    PRIMARY KEY (sku, warehouse_id)
);

CREATE TABLE processed_events (
    id UUID PRIMARY KEY
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

CREATE INDEX idx_outbox_pending ON outbox_events (created_at) WHERE published_at IS NULL;
CREATE INDEX idx_outbox_published ON outbox_events (published_at) WHERE published_at IS NOT NULL;
