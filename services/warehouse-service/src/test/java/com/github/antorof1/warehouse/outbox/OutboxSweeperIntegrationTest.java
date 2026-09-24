package com.github.antorof1.warehouse.outbox;

import com.github.antorof1.warehouse.IntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static com.github.antorof1.warehouse.utils.OutboxUtils.exists;
import static com.github.antorof1.warehouse.utils.OutboxUtils.insert;
import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@Transactional
class OutboxSweeperIntegrationTest {
    @Autowired
    private OutboxSweeper outboxSweeper;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void purgesEventsPublishedLongerAgoThanTheConfiguredRetention() {
        Instant now = Instant.now();
        UUID expired = insert(entityManager, now.minus(Duration.ofDays(8)), now.minus(Duration.ofDays(8)));
        UUID retained = insert(entityManager, now.minus(Duration.ofHours(1)), now.minus(Duration.ofDays(8)));
        UUID pending = insert(entityManager, null, now.minus(Duration.ofDays(30)));

        outboxSweeper.run();

        assertThat(exists(entityManager, expired)).isFalse();
        assertThat(exists(entityManager, retained)).isTrue();
        assertThat(exists(entityManager, pending)).isTrue();
    }
}
