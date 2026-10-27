package com.github.antorof1.kafkaretry.testapp;

import com.github.antorof1.kafkaretry.utils.KafkaRetryUtils;
import com.github.antorof1.kafkaretry.utils.TestEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class TestEventListener {
    private final Map<String, List<Instant>> attempts = new ConcurrentHashMap<>();
    private final Map<String, TestEvent> processed = new ConcurrentHashMap<>();

    @KafkaListener(topics = KafkaRetryUtils.TOPIC)
    public void onTestEvent(TestEvent event) {
        List<Instant> eventAttempts = attempts.computeIfAbsent(event.key(), key -> new CopyOnWriteArrayList<>());
        eventAttempts.add(Instant.now());

        if (event.key().startsWith("broken")) throw new IllegalStateException("broken event " + event.key());
        if (event.key().startsWith("rejected")) throw new IllegalArgumentException("rejected event " + event.key());
        if (event.key().startsWith("flaky") && eventAttempts.size() == 1) {
            throw new IllegalStateException("flaky event " + event.key());
        }

        processed.put(event.key(), event);
    }

    public List<Instant> attemptsFor(String key) {
        return attempts.getOrDefault(key, List.of());
    }

    public boolean processed(String key) {
        return processed.containsKey(key);
    }
}
