package com.github.antorof1.outbox;

import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

public class OutboxSweeper {
    private final OutboxEventRepository outboxEventRepository;
    private final Duration retention;

    public OutboxSweeper(OutboxEventRepository outboxEventRepository,
                         OutboxProperties outboxProperties) {
        this.outboxEventRepository = outboxEventRepository;
        this.retention = outboxProperties.retention();
    }

    @Transactional
    public void run() {
        Instant cutoff = Instant.now().minus(retention);
        outboxEventRepository.deletePublishedBefore(cutoff);
    }
}
