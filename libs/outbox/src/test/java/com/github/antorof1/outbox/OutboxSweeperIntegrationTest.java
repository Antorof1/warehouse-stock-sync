package com.github.antorof1.outbox;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static com.github.antorof1.outbox.utils.OutboxUtils.exists;
import static com.github.antorof1.outbox.utils.OutboxUtils.insert;
import static org.assertj.core.api.Assertions.assertThat;

@OutboxIntegrationTest
class OutboxSweeperIntegrationTest {
    @Autowired
    private OutboxSweeper outboxSweeper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void purgesEventsPublishedLongerAgoThanTheDefaultRetention() {
        Instant now = Instant.now();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        UUID expired = transaction.execute(status -> insert(entityManager, now.minus(Duration.ofDays(8)), now.minus(Duration.ofDays(8))));
        UUID retained = transaction.execute(status -> insert(entityManager, now.minus(Duration.ofHours(1)), now.minus(Duration.ofDays(8))));
        UUID pending = transaction.execute(status -> insert(entityManager, null, now.minus(Duration.ofDays(30))));

        outboxSweeper.run();

        assertThat(exists(entityManager, expired)).isFalse();
        assertThat(exists(entityManager, retained)).isTrue();
        assertThat(exists(entityManager, pending)).isTrue();
    }
}
