package com.github.antorof1.kafkaretry;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.JacksonMapperUtils;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;

@AutoConfiguration(after = {KafkaAutoConfiguration.class, JacksonAutoConfiguration.class})
@EnableConfigurationProperties(KafkaRetryProperties.class)
@ConditionalOnProperty(prefix = "app.kafka.retry", name = "enabled", matchIfMissing = true)
public class KafkaRetryAutoConfiguration {
    @Bean
    DeadLetterTemplate deadLetterTemplate(ProducerFactory<?, ?> producerFactory, ObjectProvider<JsonMapper> jsonMapper) {
        return new DeadLetterTemplate(
            new HashMap<>(producerFactory.getConfigurationProperties()),
            jsonMapper.getIfAvailable(JacksonMapperUtils::enhancedJsonMapper)
        );
    }

    @Bean
    @ConditionalOnMissingBean(CommonErrorHandler.class)
    DefaultErrorHandler kafkaRetryErrorHandler(DeadLetterTemplate deadLetterTemplate, KafkaProperties kafkaProperties,
                                               KafkaRetryProperties retryProperties,
                                               ObjectProvider<MeterRegistry> meterRegistry) {
        String groupId = kafkaProperties.getConsumer().getGroupId();
        if (groupId == null) {
            throw new IllegalStateException("spring.kafka.consumer.group-id is required to name dead letter topics");
        }

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
            deadLetterTemplate.operations(),
            (record, exception) -> new TopicPartition(DeadLetterTopics.of(record.topic(), groupId), -1)
        );

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff(retryProperties));
        retryProperties.notRetryableExceptions().forEach(errorHandler::addNotRetryableExceptions);
        errorHandler.setRetryListeners(
            new KafkaRetryMetrics(meterRegistry.getIfAvailable(() -> Metrics.globalRegistry), groupId)
        );

        return errorHandler;
    }

    private static ExponentialBackOffWithMaxRetries backOff(KafkaRetryProperties retryProperties) {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(retryProperties.maxRetries());
        backOff.setInitialInterval(retryProperties.initialInterval().toMillis());
        backOff.setMultiplier(retryProperties.multiplier());
        backOff.setMaxInterval(retryProperties.maxInterval().toMillis());

        return backOff;
    }
}
