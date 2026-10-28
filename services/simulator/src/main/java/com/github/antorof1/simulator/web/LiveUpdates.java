package com.github.antorof1.simulator.web;

import com.github.antorof1.simulator.feed.EventFeed;
import com.github.antorof1.simulator.feed.FeedEntry;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class LiveUpdates {
    static final String SNAPSHOT_EVENT = "snapshot";
    static final String FEED_EVENT = "feed";

    private static final Duration STREAM_TIMEOUT = Duration.ofMinutes(30);

    private final SnapshotAssembler assembler;
    private final EventFeed feed;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public LiveUpdates(SnapshotAssembler assembler, EventFeed feed) {
        this.assembler = assembler;
        this.feed = feed;
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT.toMillis());
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        if (send(emitter, SNAPSHOT_EVENT, assembler.snapshot()) && send(emitter, FEED_EVENT, feed.recent())) {
            emitters.add(emitter);
        }

        return emitter;
    }

    @Scheduled(fixedDelay = 500)
    public void broadcast() {
        List<FeedEntry> entries = feed.drainUnsent();
        if (emitters.isEmpty()) return;

        Snapshot snapshot = assembler.snapshot();
        for (SseEmitter emitter : emitters) {
            boolean delivered = send(emitter, SNAPSHOT_EVENT, snapshot)
                && (entries.isEmpty() || send(emitter, FEED_EVENT, entries));
            if (!delivered) {
                emitters.remove(emitter);
            }
        }
    }

    private static boolean send(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
            return true;
        } catch (IOException | IllegalStateException e) {
            emitter.completeWithError(e);
            return false;
        }
    }
}
