package com.github.antorof1.reorder.config;

import com.github.antorof1.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {
    @Bean
    @ConditionalOnProperty(prefix = "app.kafka.topics", name = "auto-create", havingValue = "true", matchIfMissing = true)
    public NewTopic PurchaseOrderEventsTopic() {
        return TopicBuilder.name(Topics.PURCHASE_ORDER_EVENTS)
            .partitions(3)
            .replicas(-1)
            .build();
    }
}
