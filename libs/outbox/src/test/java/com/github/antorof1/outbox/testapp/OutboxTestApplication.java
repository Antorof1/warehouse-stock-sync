package com.github.antorof1.outbox.testapp;

import com.github.antorof1.outbox.utils.OutboxUtils;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;

@SpringBootApplication
public class OutboxTestApplication {
    @Bean
    NewTopic outboxTestTopic() {
        return TopicBuilder.name(OutboxUtils.TOPIC).partitions(1).build();
    }
}
