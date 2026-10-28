package com.github.antorof1.simulator.feed;

import tools.jackson.databind.JsonNode;

import java.time.Instant;

public record FeedEntry(
    String id,
    String topic,
    int partition,
    long offset,
    String key,
    String type,
    Instant timestamp,
    JsonNode payload,
    String raw,
    DeadLetter deadLetter
) {
    public record DeadLetter(String originalTopic, String group, String exception) {
    }
}
