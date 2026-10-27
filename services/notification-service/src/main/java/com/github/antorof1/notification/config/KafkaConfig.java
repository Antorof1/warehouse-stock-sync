package com.github.antorof1.notification.config;

import com.github.antorof1.events.Topics;
import com.github.antorof1.kafkaretry.DeadLetterTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.stream.Stream;

@Configuration
public class KafkaConfig {
    @Bean
    @ConditionalOnProperty(prefix = "app.kafka.topics", name = "auto-create", havingValue = "true", matchIfMissing = true)
    public KafkaAdmin.NewTopics deadLetterTopics(@Value("${spring.kafka.consumer.group-id}") String groupId) {
        return new KafkaAdmin.NewTopics(
            Stream.of(Topics.MOVEMENT_EVENTS, Topics.STOCK_LEVEL_EVENTS, Topics.PURCHASE_ORDER_EVENTS)
                .map(topic -> TopicBuilder.name(DeadLetterTopics.of(topic, groupId))
                    .partitions(3)
                    .replicas(-1)
                    .build())
                .toArray(NewTopic[]::new)
        );
    }
}
