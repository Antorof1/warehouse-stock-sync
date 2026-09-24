package com.github.antorof1.warehouse.outbox;

import com.github.antorof1.events.MovementReason;
import com.github.antorof1.events.Topics;
import com.github.antorof1.warehouse.IntegrationTest;
import com.github.antorof1.warehouse.movement.Movement;
import com.github.antorof1.warehouse.movement.MovementRepository;
import com.github.antorof1.warehouse.movement.MovementService;
import com.github.antorof1.warehouse.utils.MovementUtils;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static com.github.antorof1.warehouse.utils.MovementTopicConsumer.MOVEMENT_RECORDED_TYPE;
import static com.github.antorof1.warehouse.utils.MovementUtils.WAREHOUSE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class OutboxEventServiceIntegrationTest {
    @Autowired
    private OutboxEventService outboxEventService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private MovementService movementService;

    @Autowired
    private MovementRepository movementRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void refusesToRecordOutsideATransaction() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> outboxEventService.record(id, Topics.MOVEMENT_EVENTS, MovementUtils.SKU, MovementUtils.event()))
            .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(outboxEventRepository.findById(id)).isEmpty();
    }

    @Test
    void committedMovementLeavesAnOutboxRowUnderTheSameId() {
        String sku = MovementUtils.uniqueSku();

        Movement saved = movementService.createMovement(sku, WAREHOUSE_ID, 12, MovementReason.RECEIPT);

        OutboxEvent row = outboxEventRepository.findById(saved.getId()).orElseThrow();
        assertThat(row.getTopic()).isEqualTo(Topics.MOVEMENT_EVENTS);
        assertThat(row.getKey()).isEqualTo(sku);
        assertThat(row.getType()).isEqualTo(MOVEMENT_RECORDED_TYPE);
        assertThat(row.getCreatedAt()).as("filled by the column default").isNotNull();
    }

    @Test
    void storedPayloadKeepsEveryFieldAfterTheJsonbRoundTrip() {
        String sku = MovementUtils.uniqueSku();

        Movement saved = movementService.createMovement(sku, WAREHOUSE_ID, -4, MovementReason.PICK);

        // jsonb normalizes key order and whitespace, so compare fields rather than the raw string
        String payload = outboxEventRepository.findById(saved.getId()).orElseThrow().getPayload();
        assertThat(JsonPath.<String>read(payload, "$.id")).isEqualTo(saved.getId().toString());
        assertThat(JsonPath.<String>read(payload, "$.sku")).isEqualTo(sku);
        assertThat(JsonPath.<String>read(payload, "$.warehouseId")).isEqualTo(WAREHOUSE_ID);
        assertThat(JsonPath.<Integer>read(payload, "$.quantityDelta")).isEqualTo(-4);
        assertThat(JsonPath.<String>read(payload, "$.reason")).isEqualTo("PICK");
    }

    @Test
    void rolledBackMovementLeavesNoOutboxRow() {
        String sku = MovementUtils.uniqueSku();

        UUID id = new TransactionTemplate(transactionManager).execute(status -> {
            Movement movement = movementService.createMovement(sku, WAREHOUSE_ID, 12, MovementReason.RECEIPT);
            status.setRollbackOnly();
            return movement.getId();
        });

        assertThat(movementRepository.findById(id)).isEmpty();
        assertThat(outboxEventRepository.findById(id)).isEmpty();
    }
}
