CREATE TABLE IF NOT EXISTS processed_events (
    event_id UUID NOT NULL,
    event_topic VARCHAR(64) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id, event_topic)
);

CREATE INDEX IF NOT EXISTS idx_processed_events_processed_at ON processed_events (processed_at);
