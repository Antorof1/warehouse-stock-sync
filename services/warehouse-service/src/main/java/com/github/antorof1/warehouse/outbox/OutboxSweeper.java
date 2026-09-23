package com.github.antorof1.warehouse.outbox;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Component
public class OutboxSweeper {
    private final OutboxEventRepository outboxEventRepository;
    private final Duration retention;

    public OutboxSweeper(OutboxEventRepository outboxEventRepository,
                         @Value("${app.outbox.retention}") Duration retention) {
        this.outboxEventRepository = outboxEventRepository;
        this.retention = retention;
    }

    @Scheduled(fixedDelayString = "${app.outbox.sweep-interval}")
    @Transactional
    public void run() {
        Instant cutoff = Instant.now().minus(retention);
        outboxEventRepository.deletePublishedBefore(cutoff);
    }
}
