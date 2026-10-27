package com.github.antorof1.idempotency;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class IdempotencyAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(IdempotencyAutoConfiguration.class))
        .withBean(ProcessedEventRepository.class, () -> mock(ProcessedEventRepository.class));

    @Test
    void providesTheProcessedEventService() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(ProcessedEventService.class));
    }

    @Test
    void skipsSchemaMigrationWithoutDataSource() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(IdempotencySchemaMigration.class));
    }

    @Test
    void skipsSchemaMigrationWhenServiceOptsOut() {
        contextRunner
            .withBean(DataSource.class, () -> mock(DataSource.class))
            .withPropertyValues("app.idempotency.schema-migration=false")
            .run(context -> assertThat(context).doesNotHaveBean(IdempotencySchemaMigration.class));
    }
}
