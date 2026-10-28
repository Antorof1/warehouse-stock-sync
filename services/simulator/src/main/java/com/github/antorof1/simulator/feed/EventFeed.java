package com.github.antorof1.simulator.feed;

import com.github.antorof1.simulator.config.SimulatorProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Component
public class EventFeed {
    private final int capacity;
    private final Deque<FeedEntry> recent = new ArrayDeque<>();
    private final Deque<FeedEntry> unsent = new ArrayDeque<>();
    private final Map<String, Long> topicCounts = new TreeMap<>();

    public EventFeed(SimulatorProperties properties) {
        this.capacity = properties.feed().size();
    }

    public synchronized void append(FeedEntry entry) {
        addBounded(recent, entry);
        addBounded(unsent, entry);
        topicCounts.merge(entry.topic(), 1L, Long::sum);
    }

    public synchronized List<FeedEntry> recent() {
        return new ArrayList<>(recent);
    }

    public synchronized List<FeedEntry> drainUnsent() {
        List<FeedEntry> drained = new ArrayList<>(unsent);
        unsent.clear();

        return drained;
    }

    public synchronized long count(String topic) {
        return topicCounts.getOrDefault(topic, 0L);
    }

    public synchronized Map<String, Long> topicCounts() {
        return new TreeMap<>(topicCounts);
    }

    private void addBounded(Deque<FeedEntry> entries, FeedEntry entry) {
        entries.addLast(entry);
        while (entries.size() > capacity) {
            entries.removeFirst();
        }
    }
}
