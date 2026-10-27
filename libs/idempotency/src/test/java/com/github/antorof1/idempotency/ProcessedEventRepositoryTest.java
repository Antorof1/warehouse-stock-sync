package com.github.antorof1.idempotency;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static com.github.antorof1.idempotency.utils.IdempotencyUtils.OTHER_TOPIC;
import static com.github.antorof1.idempotency.utils.IdempotencyUtils.TOPIC;
import static com.github.antorof1.idempotency.utils.IdempotencyUtils.exists;
import static com.github.antorof1.idempotency.utils.IdempotencyUtils.insertProcessedAt;
import static com.github.antorof1.idempotency.utils.IdempotencyUtils.newEventId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IdempotencyIntegrationTest
@Transactional
class ProcessedEventRepositoryTest {
    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void storesAnEventIdAndFindsItAgain() {
        ProcessedEventId id = new ProcessedEventId(newEventId(), TOPIC);

        processedEventRepository.saveAndFlush(new ProcessedEvent(id));

        assertThat(processedEventRepository.findById(id)).isPresent();
        assertThat(processedEventRepository.existsById(id)).isTrue();
    }

    @Test
    void doesNotKnowAnEventItNeverStored() {
        assertThat(processedEventRepository.existsById(new ProcessedEventId(newEventId(), TOPIC))).isFalse();
    }

    @Test
    void insertsANewEventIdAndCountsTheWrite() {
        UUID id = newEventId();

        assertThat(processedEventRepository.insertIgnoreConflict(id, TOPIC)).isEqualTo(1);

        assertThat(rowCountFor(id)).isEqualTo(1);
    }

    @Test
    void countsNoWriteWhenTheEventIsAlreadyStoredForTheTopic() {
        UUID id = newEventId();
        processedEventRepository.insertIgnoreConflict(id, TOPIC);

        assertThat(processedEventRepository.insertIgnoreConflict(id, TOPIC))
            .as("ON CONFLICT DO NOTHING turns the duplicate into an empty write instead of an error")
            .isZero();

        assertThat(rowCountFor(id)).isEqualTo(1);
    }

    @Test
    void storesTheSameEventIdOnceForEachTopic() {
        UUID id = newEventId();
        processedEventRepository.insertIgnoreConflict(id, TOPIC);

        assertThat(processedEventRepository.insertIgnoreConflict(id, OTHER_TOPIC)).isEqualTo(1);

        assertThat(rowCountFor(id)).isEqualTo(2);
    }

    @Test
    void countsNoWriteForAnEventStoredThroughJpa() {
        UUID id = newEventId();
        processedEventRepository.saveAndFlush(new ProcessedEvent(new ProcessedEventId(id, TOPIC)));

        assertThat(processedEventRepository.insertIgnoreConflict(id, TOPIC)).isZero();
    }

    @Test
    void refusesADuplicateInsertAtTheDatabaseLevel() {
        UUID id = newEventId();
        insert(id, TOPIC);

        assertThatThrownBy(() -> insert(id, TOPIC))
            .as("the primary key is what ON CONFLICT DO NOTHING relies on when two consumers race on one event")
            .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void stampsWhenAnEventWasProcessed() {
        ProcessedEventId id = new ProcessedEventId(newEventId(), TOPIC);
        Instant before = Instant.now().minusSeconds(5);

        processedEventRepository.insertIgnoreConflict(id.eventId(), id.eventTopic());

        assertThat(processedEventRepository.findById(id))
            .get()
            .extracting(ProcessedEvent::getProcessedAt)
            .satisfies(processedAt -> assertThat(processedAt).isAfter(before));
    }

    @Test
    void deleteProcessedBeforeRemovesOnlyOldEvents() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(14));
        UUID expired = insertProcessedAt(jdbcTemplate, cutoff.minus(Duration.ofDays(1)));
        UUID recent = insertProcessedAt(jdbcTemplate, cutoff.plus(Duration.ofDays(1)));

        int deleted = processedEventRepository.deleteProcessedBefore(cutoff);

        assertThat(deleted).isPositive();
        assertThat(exists(jdbcTemplate, expired)).isFalse();
        assertThat(exists(jdbcTemplate, recent)).isTrue();
    }

    private void insert(UUID id, String topic) {
        jdbcTemplate.update("INSERT INTO processed_events (event_id, event_topic) VALUES (?, ?)", id, topic);
    }

    private long rowCountFor(UUID id) {
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM processed_events WHERE event_id = ?", Long.class, id
        );

        return count == null ? 0 : count;
    }
}
