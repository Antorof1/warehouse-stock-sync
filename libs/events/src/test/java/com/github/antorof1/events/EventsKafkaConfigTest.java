package com.github.antorof1.events;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.ProducerFactory;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EventsKafkaConfigTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withConfiguration(AutoConfigurations.of(KafkaAutoConfiguration.class))
        .withPropertyValues("spring.config.import=classpath:events-kafka.yaml");

    @Test
    void producersWaitForAllInSyncReplicasAndDeduplicateRetries() {
        contextRunner.run(context -> assertThat(producerConfig(context.getBean(ProducerFactory.class)))
            .containsEntry(ProducerConfig.ACKS_CONFIG, "all")
            .containsEntry(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true")
            .containsEntry(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "5")
            .containsEntry(ProducerConfig.RETRIES_CONFIG, "2147483647"));
    }

    @Test
    void producersGiveUpWithinBoundedTimeouts() {
        contextRunner.run(context -> assertThat(producerConfig(context.getBean(ProducerFactory.class)))
            .containsEntry(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, "30000")
            .containsEntry(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, "60000")
            .containsEntry(ProducerConfig.MAX_BLOCK_MS_CONFIG, "10000"));
    }

    @Test
    void servicesCanOverrideReliabilitySettings() {
        contextRunner
            .withPropertyValues("spring.kafka.producer.properties.delivery.timeout.ms=90000")
            .run(context -> assertThat(producerConfig(context.getBean(ProducerFactory.class)))
                .containsEntry(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, "90000"));
    }

    private static Map<String, Object> producerConfig(ProducerFactory<?, ?> producerFactory) {
        return producerFactory.getConfigurationProperties();
    }
}
