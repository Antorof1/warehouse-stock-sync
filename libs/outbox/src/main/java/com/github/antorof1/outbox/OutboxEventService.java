package com.github.antorof1.outbox;

import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class OutboxEventService {
    private static final String TYPE_MAPPING_PROPERTY = "spring.json.type.mapping";

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final Map<String, String> typesByClassName;

    public OutboxEventService(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper,
                              KafkaProperties kafkaProperties) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.typesByClassName = parseTypeMapping(kafkaProperties.getProducer().getProperties().get(TYPE_MAPPING_PROPERTY));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID id, String topic, String key, Object event) {
        outboxEventRepository.save(new OutboxEvent(
            id,
            topic,
            key,
            typeOf(event),
            objectMapper.writeValueAsString(event)
        ));
    }

    private String typeOf(Object event) {
        String type = typesByClassName.get(event.getClass().getName());

        if (type == null) {
            throw new IllegalArgumentException("No " + TYPE_MAPPING_PROPERTY + " entry for " + event.getClass().getName());
        }

        return type;
    }

    private static Map<String, String> parseTypeMapping(String mapping) {
        if (!StringUtils.hasText(mapping)) return Map.of();

        return Arrays.stream(mapping.split(","))
            .map(String::trim)
            .filter(entry -> !entry.isEmpty())
            .map(entry -> entry.split(":", 2))
            .collect(Collectors.toMap(pair -> pair[1].trim(), pair -> pair[0].trim()));
    }
}
