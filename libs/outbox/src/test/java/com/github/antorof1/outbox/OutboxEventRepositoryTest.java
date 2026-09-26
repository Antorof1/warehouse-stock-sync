package com.github.antorof1.outbox;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.github.antorof1.outbox.utils.OutboxUtils.exists;
import static com.github.antorof1.outbox.utils.OutboxUtils.insert;
import static com.github.antorof1.outbox.utils.OutboxUtils.publishedAt;
import static org.assertj.core.api.Assertions.assertThat;

@OutboxIntegrationTest
@Transactional
class OutboxEventRepositoryTest {
    private static final Instant NOW = Instant.now();

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void findPendingSkipsPublishedEvents() {
        UUID pending = insert(entityManager, null, NOW);
        UUID published = insert(entityManager, NOW, NOW);

        List<UUID> found = pendingIds();

        assertThat(found).contains(pending).doesNotContain(published);
    }

    @Test
    void findPendingReturnsOldestFirst() {
        UUID newest = insert(entityManager, null, NOW.minusSeconds(1));
        UUID oldest = insert(entityManager, null, NOW.minusSeconds(3));
        UUID middle = insert(entityManager, null, NOW.minusSeconds(2));

        List<UUID> found = pendingIds().stream().filter(List.of(newest, oldest, middle)::contains).toList();

        assertThat(found).containsExactly(oldest, middle, newest);
    }

    @Test
    void findPendingRespectsTheLimit() {
        insert(entityManager, null, NOW);
        insert(entityManager, null, NOW);
        insert(entityManager, null, NOW);

        assertThat(outboxEventRepository.findPending(2)).hasSize(2);
    }

    @Test
    void markPublishedStampsOnlyTheGivenIds() {
        UUID marked = insert(entityManager, null, NOW);
        UUID untouched = insert(entityManager, null, NOW);

        outboxEventRepository.markPublished(List.of(marked));

        assertThat(publishedAt(entityManager, marked)).isNotNull();
        assertThat(publishedAt(entityManager, untouched)).isNull();
    }

    @Test
    void deletePublishedBeforeRemovesOnlyOldPublishedEvents() {
        Instant cutoff = NOW.minus(Duration.ofDays(7));
        UUID expired = insert(entityManager, cutoff.minus(Duration.ofDays(1)), cutoff.minus(Duration.ofDays(1)));
        UUID recent = insert(entityManager, NOW.minus(Duration.ofHours(1)), cutoff.minus(Duration.ofDays(1)));
        UUID oldButPending = insert(entityManager, null, cutoff.minus(Duration.ofDays(30)));

        outboxEventRepository.deletePublishedBefore(cutoff);

        assertThat(exists(entityManager, expired)).isFalse();
        assertThat(exists(entityManager, recent)).isTrue();
        assertThat(exists(entityManager, oldButPending)).as("an unpublished event is never swept").isTrue();
    }

    private List<UUID> pendingIds() {
        return outboxEventRepository.findPending(Integer.MAX_VALUE).stream().map(OutboxEvent::getId).toList();
    }
}
