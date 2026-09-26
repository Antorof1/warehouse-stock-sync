package com.github.antorof1.outbox;

import com.github.antorof1.outbox.utils.OutboxUtils;
import com.github.antorof1.outbox.utils.TestEvent;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static com.github.antorof1.outbox.utils.OutboxUtils.KEY;
import static com.github.antorof1.outbox.utils.OutboxUtils.TOPIC;
import static com.github.antorof1.outbox.utils.OutboxUtils.TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class OutboxEventServiceTest {
    private static final String TYPE_MAPPING_PROPERTY = "spring.json.type.mapping";
    private static final UUID ID = UUID.fromString("0199a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b");

    private static final String FOLDED_MAPPING = """
        %s, \
        other.event:com.example.OtherEvent,\s
        """.formatted(OutboxUtils.TYPE_MAPPING);

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Captor
    private ArgumentCaptor<OutboxEvent> outboxEventCaptor;

    @Test
    void savesRowWithIdTopicAndKey() {
        service(FOLDED_MAPPING).record(ID, TOPIC, KEY, OutboxUtils.event());

        OutboxEvent saved = captured();
        assertThat(saved.getId()).isEqualTo(ID);
        assertThat(saved.getTopic()).isEqualTo(TOPIC);
        assertThat(saved.getKey()).isEqualTo(KEY);
        assertThat(saved.getPublishedAt()).as("new rows are pending").isNull();
    }

    @Test
    void storesTheLogicalTypeNameNotTheJavaClass() {
        service(FOLDED_MAPPING).record(ID, TOPIC, KEY, OutboxUtils.event());

        assertThat(captured().getType()).isEqualTo(TYPE);
    }

    @Test
    void serializesEveryEventFieldIntoThePayload() {
        TestEvent event = OutboxUtils.event();

        service(FOLDED_MAPPING).record(ID, TOPIC, KEY, event);

        String payload = captured().getPayload();
        assertThat(JsonPath.<String>read(payload, "$.id")).isEqualTo(event.id().toString());
        assertThat(JsonPath.<String>read(payload, "$.name")).isEqualTo(event.name());
        assertThat(JsonPath.<Integer>read(payload, "$.amount")).isEqualTo(event.amount());
    }

    @Test
    void toleratesWhitespaceAndEmptyEntriesInTheMapping() {
        String messy = "  \n %s : %s ,, \n".formatted(TYPE, TestEvent.class.getName());

        service(messy).record(ID, TOPIC, KEY, OutboxUtils.event());

        assertThat(captured().getType()).isEqualTo(TYPE);
    }

    @Test
    void rejectsEventWithoutATypeMappingAndSavesNothing() {
        OutboxEventService service = service("other.event:com.example.OtherEvent");

        assertThatThrownBy(() -> service.record(ID, TOPIC, KEY, OutboxUtils.event()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(TestEvent.class.getName());

        verifyNoInteractions(outboxEventRepository);
    }

    @Test
    void missingMappingPropertyRejectsEveryEvent() {
        OutboxEventService service = service(null);

        assertThatThrownBy(() -> service.record(ID, TOPIC, KEY, OutboxUtils.event()))
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
