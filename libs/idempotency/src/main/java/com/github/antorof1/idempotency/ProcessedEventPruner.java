package com.github.antorof1.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

public class ProcessedEventPruner {
    private static final Logger log = LoggerFactory.getLogger(ProcessedEventPruner.class);

    private final ProcessedEventRepository processedEventRepository;
    private final Duration retention;

    public ProcessedEventPruner(ProcessedEventRepository processedEventRepository,
                                IdempotencyProperties idempotencyProperties) {
        this.processedEventRepository = processedEventRepository;
        this.retention = idempotencyProperties.pruning().retention();
    }

    @Transactional
    public void run() {
        Instant cutoff = Instant.now().minus(retention);
        int pruned = processedEventRepository.deleteProcessedBefore(cutoff);

        if (pruned > 0) {
            log.info("Pruned {} processed events older than {}", pruned, cutoff);
        }
    }
}
