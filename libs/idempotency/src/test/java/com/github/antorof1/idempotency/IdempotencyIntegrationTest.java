package com.github.antorof1.idempotency;

import com.github.antorof1.idempotency.testapp.IdempotencyTestApplication;
import com.github.antorof1.idempotency.testapp.TestcontainersConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(classes = IdempotencyTestApplication.class)
@Import(TestcontainersConfiguration.class)
public @interface IdempotencyIntegrationTest {
}
