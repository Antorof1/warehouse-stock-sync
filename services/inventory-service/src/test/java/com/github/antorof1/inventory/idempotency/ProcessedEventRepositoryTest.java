package com.github.antorof1.inventory.idempotency;

import com.github.antorof1.inventory.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.github.antorof1.inventory.utils.StockUtils.newEventId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
        UUID id = newEventId();

        processedEventRepository.saveAndFlush(new ProcessedEvent(id));

        assertThat(processedEventRepository.findById(id)).isPresent();
        assertThat(processedEventRepository.existsById(id)).isTrue();
    }

    @Test
    void doesNotKnowAnEventItNeverStored() {
        assertThat(processedEventRepository.existsById(newEventId())).isFalse();
    }

    @Test
    void acceptsTheSameIdTwiceBecauseSaveMerges() {
        UUID id = newEventId();
        processedEventRepository.saveAndFlush(new ProcessedEvent(id));

        assertThatCode(() -> processedEventRepository.saveAndFlush(new ProcessedEvent(id)))
            .as("the assigned id makes save() a merge, so idempotency rests on the isProcessed check")
            .doesNotThrowAnyException();

        assertThat(rowCountFor(id)).isEqualTo(1);
    }

    @Test
    void refusesADuplicateInsertAtTheDatabaseLevel() {
        UUID id = newEventId();
        insert(id);

        assertThatThrownBy(() -> insert(id))
            .as("the primary key is the last line of defence when two consumers race on one event")
            .isInstanceOf(DuplicateKeyException.class);
    }

    private void insert(UUID id) {
        jdbcTemplate.update("INSERT INTO processed_events (id) VALUES (?)", id);
    }

    private long rowCountFor(UUID id) {
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM processed_events WHERE id = ?", Long.class, id
        );

        return count == null ? 0 : count;
    }
}
