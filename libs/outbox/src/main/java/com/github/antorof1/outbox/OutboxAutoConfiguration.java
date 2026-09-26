package com.github.antorof1.outbox;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import tools.jackson.databind.ObjectMapper;

@AutoConfiguration(
    before = {HibernateJpaAutoConfiguration.class, DataJpaRepositoriesAutoConfiguration.class},
    after = KafkaAutoConfiguration.class
)
@AutoConfigurationPackage(basePackageClasses = OutboxEvent.class)
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxAutoConfiguration {
    @Bean
    OutboxEventService outboxEventService(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper,
                                          KafkaProperties kafkaProperties) {
        return new OutboxEventService(outboxEventRepository, objectMapper, kafkaProperties);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "app.outbox", name = "enabled", matchIfMissing = true)
    @EnableScheduling
    static class PublishingConfiguration {
        @Bean
        OutboxPublisher outboxPublisher(OutboxEventRepository outboxEventRepository,
                                        KafkaTemplate<String, String> kafkaTemplate,
                                        OutboxProperties outboxProperties) {
            return new OutboxPublisher(outboxEventRepository, kafkaTemplate, outboxProperties);
        }

        @Bean
        OutboxSweeper outboxSweeper(OutboxEventRepository outboxEventRepository, OutboxProperties outboxProperties) {
            return new OutboxSweeper(outboxEventRepository, outboxProperties);
        }

        @Bean
        SchedulingConfigurer outboxScheduling(OutboxPublisher outboxPublisher, OutboxSweeper outboxSweeper,
                                              OutboxProperties outboxProperties) {
            return register -> {
                register.addFixedDelayTask(outboxPublisher::poll, outboxProperties.pollInterval());
                register.addFixedDelayTask(outboxSweeper::run, outboxProperties.sweepInterval());
            };
        }
    }
}
