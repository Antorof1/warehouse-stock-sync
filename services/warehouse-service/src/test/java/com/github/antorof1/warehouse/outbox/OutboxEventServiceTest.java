package com.github.antorof1.warehouse.outbox;

import com.github.antorof1.events.MovementEvent;
import com.github.antorof1.events.Topics;
import com.github.antorof1.warehouse.utils.MovementUtils;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

import static com.github.antorof1.warehouse.utils.MovementUtils.SKU;
import static com.github.antorof1.warehouse.utils.MovementUtils.WAREHOUSE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class OutboxEventServiceTest {
    private static final String TYPE_MAPPING_PROPERTY = "spring.json.type.mapping";
    private static final UUID ID = UUID.fromString("0199a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b");

    private static final String FOLDED_MAPPING = """
        warehouse.movement.recorded:com.github.antorof1.events.MovementEvent, \
        inventory.stock-level.changed:com.github.antorof1.events.StockLevelEvent,\s
        """;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Captor
    private ArgumentCaptor<OutboxEvent> outboxEventCaptor;

    @Test
    void savesRowWithIdTopicAndKey() {
        service(FOLDED_MAPPING).record(ID, Topics.MOVEMENT_EVENTS, SKU, MovementUtils.event());

        OutboxEvent saved = captured();
        assertThat(saved.getId()).isEqualTo(ID);
        assertThat(saved.getTopic()).isEqualTo(Topics.MOVEMENT_EVENTS);
        assertThat(saved.getKey()).isEqualTo(SKU);
        assertThat(saved.getPublishedAt()).as("new rows are pending").isNull();
    }

    @Test
    void storesTheLogicalTypeNameNotTheJavaClass() {
        service(FOLDED_MAPPING).record(ID, Topics.MOVEMENT_EVENTS, SKU, MovementUtils.event());

        assertThat(captured().getType()).isEqualTo("warehouse.movement.recorded");
    }

    @Test
    void serializesEveryEventFieldIntoThePayload() {
        MovementEvent event = MovementUtils.event();

        service(FOLDED_MAPPING).record(ID, Topics.MOVEMENT_EVENTS, SKU, event);

        String payload = captured().getPayload();
        assertThat(JsonPath.<String>read(payload, "$.id")).isEqualTo(event.id().toString());
        assertThat(JsonPath.<String>read(payload, "$.sku")).isEqualTo(SKU);
        assertThat(JsonPath.<String>read(payload, "$.warehouseId")).isEqualTo(WAREHOUSE_ID);
        assertThat(JsonPath.<Integer>read(payload, "$.quantityDelta")).isEqualTo(event.quantityDelta());
        assertThat(JsonPath.<String>read(payload, "$.reason")).isEqualTo(event.reason().name());
        assertThat(Instant.parse(JsonPath.read(payload, "$.occurredAt"))).isEqualTo(event.occurredAt());
    }

    @Test
    void toleratesWhitespaceAndEmptyEntriesInTheMapping() {
        String messy = "  \n warehouse.movement.recorded : com.github.antorof1.events.MovementEvent ,, \n";

        service(messy).record(ID, Topics.MOVEMENT_EVENTS, SKU, MovementUtils.event());

        assertThat(captured().getType()).isEqualTo("warehouse.movement.recorded");
    }

    @Test
    void rejectsEventWithoutATypeMappingAndSavesNothing() {
        OutboxEventService service = service("inventory.stock-level.changed:com.github.antorof1.events.StockLevelEvent");

        assertThatThrownBy(() -> service.record(ID, Topics.MOVEMENT_EVENTS, SKU, MovementUtils.event()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(MovementEvent.class.getName());

        verifyNoInteractions(outboxEventRepository);
    }

    @Test
    void missingMappingPropertyRejectsEveryEvent() {
        OutboxEventService service = service(null);

        assertThatThrownBy(() -> service.record(ID, Topics.MOVEMENT_EVENTS, SKU, MovementUtils.event()))
            .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(outboxEventRepository);
    }

    private OutboxEventService service(String mapping) {
        KafkaProperties kafkaProperties = new KafkaProperties();
        if (mapping != null) {
            kafkaProperties.getProducer().getProperties().put(TYPE_MAPPING_PROPERTY, mapping);
        }

        return new OutboxEventService(outboxEventRepository, JsonMapper.builder().build(), kafkaProperties);
    }

    private OutboxEvent captured() {
        verify(outboxEventRepository).save(outboxEventCaptor.capture());

        return outboxEventCaptor.getValue();
    }
}
