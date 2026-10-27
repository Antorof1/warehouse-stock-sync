package com.github.antorof1.kafkaretry;

import com.github.antorof1.kafkaretry.testapp.KafkaRetryTestApplication;
import com.github.antorof1.kafkaretry.testapp.TestcontainersConfiguration;
import com.github.antorof1.kafkaretry.utils.KafkaRetryUtils;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(classes = KafkaRetryTestApplication.class, properties = {
    "spring.kafka.consumer.group-id=" + KafkaRetryUtils.GROUP_ID,
    "spring.kafka.consumer.auto-offset-reset=earliest",
    "spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.ErrorHandlingDeserializer",
    "spring.kafka.consumer.properties[spring.deserializer.value.delegate.class]=org.springframework.kafka.support.serializer.JacksonJsonDeserializer",
    "spring.kafka.consumer.properties[spring.json.trusted.packages]=com.github.antorof1.kafkaretry.utils",
    "spring.kafka.consumer.properties[spring.json.type.mapping]=" + KafkaRetryUtils.TYPE_MAPPING,
    "spring.kafka.producer.properties[spring.json.type.mapping]=" + KafkaRetryUtils.TYPE_MAPPING,
    "app.kafka.retry.max-retries=" + KafkaRetryUtils.MAX_RETRIES,
    "app.kafka.retry.initial-interval=" + KafkaRetryUtils.INITIAL_INTERVAL_MILLIS + "ms",
    "app.kafka.retry.not-retryable-exceptions=java.lang.IllegalArgumentException"
})
@Import(TestcontainersConfiguration.class)
public @interface KafkaRetryIntegrationTest {
}
