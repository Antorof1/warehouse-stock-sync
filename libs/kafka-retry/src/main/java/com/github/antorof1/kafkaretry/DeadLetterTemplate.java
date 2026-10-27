package com.github.antorof1.kafkaretry;

import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;

public class DeadLetterTemplate implements DisposableBean {
    private final DefaultKafkaProducerFactory<Object, Object> producerFactory;
    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @SuppressWarnings({"unchecked", "rawtypes"})
    public DeadLetterTemplate(Map<String, Object> producerProperties, JsonMapper jsonMapper) {
        Map<Class<?>, Serializer<?>> valueSerializers = new LinkedHashMap<>();
        valueSerializers.put(byte[].class, new ByteArraySerializer());
        valueSerializers.put(Object.class, new JacksonJsonSerializer<>(jsonMapper));

        this.producerFactory = new DefaultKafkaProducerFactory<>(
            producerProperties,
            (Serializer) new StringSerializer(),
            new DelegatingByTypeSerializer(valueSerializers, true)
        );
        this.kafkaTemplate = new KafkaTemplate<>(producerFactory);
    }

    public KafkaOperations<Object, Object> operations() {
        return kafkaTemplate;
    }

    @Override
    public void destroy() {
        producerFactory.destroy();
    }
}
