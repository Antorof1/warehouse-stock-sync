package com.github.antorof1.kafkaretry;

public final class DeadLetterTopics {
    public static final String SUFFIX = ".dlt";

    private DeadLetterTopics() {
    }

    public static String of(String topic, String groupId) {
        return topic + "." + groupId + SUFFIX;
    }
}
