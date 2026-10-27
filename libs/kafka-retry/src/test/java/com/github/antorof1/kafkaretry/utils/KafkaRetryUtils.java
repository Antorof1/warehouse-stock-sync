package com.github.antorof1.kafkaretry.utils;

import com.github.antorof1.kafkaretry.DeadLetterTopics;

import java.util.UUID;

public final class KafkaRetryUtils {
    public static final String TOPIC = "kafka-retry-test-events";
    public static final String GROUP_ID = "kafka-retry-tests";
    public static final String DEAD_LETTER_TOPIC = DeadLetterTopics.of(TOPIC, GROUP_ID);
    public static final String TYPE = "kafka-retry.test.event";
    public static final String TYPE_MAPPING = TYPE + ":com.github.antorof1.kafkaretry.utils.TestEvent";
    public static final int MAX_RETRIES = 2;
    public static final long INITIAL_INTERVAL_MILLIS = 200;

    private KafkaRetryUtils() {
    }

    public static String uniqueKey(String behaviour) {
        return behaviour + "-" + UUID.randomUUID();
    }

    public static String payload(String key) {
        return "{\"key\": \"" + key + "\", \"name\": \"widget\"}";
    }
}
