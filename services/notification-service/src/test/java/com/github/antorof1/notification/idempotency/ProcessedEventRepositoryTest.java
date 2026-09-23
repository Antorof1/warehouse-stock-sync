package com.github.antorof1.notification.idempotency;

import com.github.antorof1.events.Topics;
import com.github.antorof1.notification.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.github.antorof1.notification.utils.NotificationUtils.newEventId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
@Transactional
class ProcessedEventRepositoryTest {
    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void storesAnEventIdAndFindsItAgain() {
        ProcessedEventId id = new ProcessedEventId(newEventId(), Topics.MOVEMENT_EVENTS);

        processedEventRepository.saveAndFlush(new ProcessedEvent(id));

        assertThat(processedEventRepository.existsById(id)).isTrue();
    }

    @Test
    void insertsANewEventIdAndCountsTheWrite() {
        UUID id = newEventId();

        assertThat(processedEventRepository.insertIgnoreConflict(id, Topics.MOVEMENT_EVENTS)).isEqualTo(1);

        assertThat(rowCountFor(id)).isEqualTo(1);
    }

    @Test
    void countsNoWriteWhenTheEventIsAlreadyStoredForTheTopic() {
        UUID id = newEventId();
        processedEventRepository.insertIgnoreConflict(id, Topics.MOVEMENT_EVENTS);

        assertThat(processedEventRepository.insertIgnoreConflict(id, Topics.MOVEMENT_EVENTS))
            .as("ON CONFLICT DO NOTHING turns the duplicate into an empty write instead of an error")
            .isZero();

        assertThat(rowCountFor(id)).isEqualTo(1);
    }

    @Test
    void storesTheSameEventIdOnceForEachTopic() {
        UUID id = newEventId();
        processedEventRepository.insertIgnoreConflict(id, Topics.MOVEMENT_EVENTS);

        assertThat(processedEventRepository.insertIgnoreConflict(id, Topics.STOCK_LEVEL_EVENTS)).isEqualTo(1);

        assertThat(rowCountFor(id)).isEqualTo(2);
    }

    @Test
    void countsNoWriteForAnEventStoredThroughJpa() {
        UUID id = newEventId();
        processedEventRepository.saveAndFlush(new ProcessedEvent(new ProcessedEventId(id, Topics.MOVEMENT_EVENTS)));

        assertThat(processedEventRepository.insertIgnoreConflict(id, Topics.MOVEMENT_EVENTS)).isZero();
    }

    @Test
    void refusesADuplicateInsertAtTheDatabaseLevel() {
        UUID id = newEventId();
        insert(id, Topics.MOVEMENT_EVENTS);

        assertThatThrownBy(() -> insert(id, Topics.MOVEMENT_EVENTS))
            .as("the primary key is what ON CONFLICT DO NOTHING relies on when two consumers race on one event")
            .isInstanceOf(DuplicateKeyException.class);
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
