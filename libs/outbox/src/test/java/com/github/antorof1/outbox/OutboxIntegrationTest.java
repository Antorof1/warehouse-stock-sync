package com.github.antorof1.outbox;

import com.github.antorof1.outbox.testapp.OutboxTestApplication;
import com.github.antorof1.outbox.testapp.TestcontainersConfiguration;
import com.github.antorof1.outbox.utils.OutboxUtils;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(classes = OutboxTestApplication.class, properties = {
    "spring.flyway.locations=classpath:db/outbox-test",
    "spring.kafka.producer.properties[spring.json.type.mapping]=" + OutboxUtils.TYPE_MAPPING
})
@Import(TestcontainersConfiguration.class)
public @interface OutboxIntegrationTest {
}
