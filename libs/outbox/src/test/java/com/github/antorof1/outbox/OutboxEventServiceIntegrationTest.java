package com.github.antorof1.outbox;

import com.github.antorof1.outbox.utils.OutboxUtils;
import com.github.antorof1.outbox.utils.TestEvent;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

import static com.github.antorof1.outbox.utils.OutboxUtils.TOPIC;
import static com.github.antorof1.outbox.utils.OutboxUtils.TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@OutboxIntegrationTest
class OutboxEventServiceIntegrationTest {
    @Autowired
    private OutboxEventService outboxEventService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void refusesToRecordOutsideATransaction() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> outboxEventService.record(id, TOPIC, OutboxUtils.uniqueKey(), OutboxUtils.event(id)))
            .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(outboxEventRepository.findById(id)).isEmpty();
    }

    @Test
    void rowRollsBackWithTheCallersTransaction() {
        UUID id = UUID.randomUUID();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            outboxEventService.record(id, TOPIC, OutboxUtils.uniqueKey(), OutboxUtils.event(id));
            status.setRollbackOnly();
        });

        assertThat(outboxEventRepository.findById(id)).isEmpty();
    }

    @Test
    void committedRowKeepsEveryFieldAfterTheJsonbRoundTrip() {
        UUID id = UUID.randomUUID();
        String key = OutboxUtils.uniqueKey();
        TestEvent event = OutboxUtils.event(id);

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
            outboxEventService.record(id, TOPIC, key, event)
        );

        OutboxEvent row = outboxEventRepository.findById(id).orElseThrow();
        assertThat(row.getTopic()).isEqualTo(TOPIC);
        assertThat(row.getKey()).isEqualTo(key);
        assertThat(row.getType()).isEqualTo(TYPE);
        assertThat(row.getCreatedAt()).as("filled by the column default").isNotNull();

        String payload = row.getPayload();
        assertThat(JsonPath.<String>read(payload, "$.id")).isEqualTo(id.toString());
        assertThat(JsonPath.<String>read(payload, "$.name")).isEqualTo(event.name());
        assertThat(JsonPath.<Integer>read(payload, "$.amount")).isEqualTo(event.amount());
        assertThat(Instant.parse(JsonPath.read(payload, "$.occurredAt")))
            .as("the application's ObjectMapper writes instants as ISO-8601 at full precision")
            .isEqualTo(event.occurredAt());
    }
}
