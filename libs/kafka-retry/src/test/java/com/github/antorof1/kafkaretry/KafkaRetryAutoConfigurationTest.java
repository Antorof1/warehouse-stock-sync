package com.github.antorof1.kafkaretry;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KafkaRetryAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            KafkaRetryAutoConfiguration.class, KafkaAutoConfiguration.class, JacksonAutoConfiguration.class
        ))
        .withPropertyValues("spring.kafka.consumer.group-id=test-service");

    @Test
    void installsTheErrorHandlerOnTheListenerContainerFactory() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(DefaultErrorHandler.class).hasSingleBean(DeadLetterTemplate.class);

            ConcurrentKafkaListenerContainerFactory<?, ?> factory =
                context.getBean(ConcurrentKafkaListenerContainerFactory.class);
            assertThat(factory).extracting("commonErrorHandler").isSameAs(context.getBean(DefaultErrorHandler.class));
        });
    }

    @Test
    void leavesTheServiceKafkaTemplateAndProducerFactoryInPlace() {
        contextRunner.run(context -> assertThat(context)
            .hasSingleBean(KafkaTemplate.class)
            .hasSingleBean(ProducerFactory.class));
    }

    @Test
    void installsNothingWhenDisabled() {
        contextRunner.withPropertyValues("app.kafka.retry.enabled=false").run(context -> assertThat(context)
            .doesNotHaveBean(DefaultErrorHandler.class)
            .doesNotHaveBean(DeadLetterTemplate.class));
    }

    @Test
    void backsOffWhenServiceProvidesItsOwnErrorHandler() {
        CommonErrorHandler serviceErrorHandler = mock(CommonErrorHandler.class);

        contextRunner
            .withBean(CommonErrorHandler.class, () -> serviceErrorHandler)
            .run(context -> assertThat(context.getBean(CommonErrorHandler.class)).isSameAs(serviceErrorHandler));
    }

    @Test
    void failsFastWithoutAConsumerGroup() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                KafkaRetryAutoConfiguration.class, KafkaAutoConfiguration.class, JacksonAutoConfiguration.class
            ))
            .run(context -> assertThat(context).getFailure()
                .rootCause()
                .hasMessageContaining("spring.kafka.consumer.group-id"));
    }

    @Test
    void servicesWithoutRetrySettingsGetTheDefaults() {
        contextRunner.run(context -> {
            KafkaRetryProperties properties = context.getBean(KafkaRetryProperties.class);

            assertThat(properties.enabled()).isTrue();
            assertThat(properties.maxRetries()).isEqualTo(4);
            assertThat(properties.initialInterval()).isEqualTo(Duration.ofSeconds(1));
            assertThat(properties.multiplier()).isEqualTo(2.0);
            assertThat(properties.maxInterval()).isEqualTo(Duration.ofSeconds(10));
            assertThat(properties.notRetryableExceptions()).isEmpty();
        });
    }

    @Test
    void bindsNotRetryableExceptionsByClassName() {
        contextRunner
            .withPropertyValues("app.kafka.retry.not-retryable-exceptions=java.lang.IllegalArgumentException")
            .run(context -> assertThat(context.getBean(KafkaRetryProperties.class).notRetryableExceptions())
                .containsExactly(IllegalArgumentException.class));
    }

    @Test
    void namesDeadLetterTopicsPerConsumerGroup() {
        assertThat(DeadLetterTopics.of("warehouse.movement-events", "inventory-service"))
            .isEqualTo("warehouse.movement-events.inventory-service.dlt");
    }
}
