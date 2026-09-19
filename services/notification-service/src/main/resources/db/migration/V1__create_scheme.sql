CREATE TABLE processed_events (
    event_id UUID NOT NULL,
    event_topic VARCHAR(64) NOT NULL,
    PRIMARY KEY (event_id, event_topic)
);
