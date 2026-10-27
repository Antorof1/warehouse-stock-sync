package com.github.antorof1.kafkaretry.testapp;

import com.github.antorof1.kafkaretry.utils.KafkaRetryUtils;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;

@SpringBootApplication
public class KafkaRetryTestApplication {
    @Bean
    NewTopic kafkaRetryTestTopic() {
        return TopicBuilder.name(KafkaRetryUtils.TOPIC).partitions(1).build();
    }

    @Bean
    NewTopic kafkaRetryTestDeadLetterTopic() {
        return TopicBuilder.name(KafkaRetryUtils.DEAD_LETTER_TOPIC).partitions(1).build();
    }
}
