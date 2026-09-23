CREATE TABLE movements (
    id UUID PRIMARY KEY,
    sku VARCHAR(64) NOT NULL,
    warehouse_id VARCHAR(64) NOT NULL,
    quantity_delta INTEGER NOT NULL,
    reason VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL
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
