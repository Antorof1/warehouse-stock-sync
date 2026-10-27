package com.github.antorof1.idempotency;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.SchedulingConfigurer;

import javax.sql.DataSource;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class IdempotencyAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(IdempotencyAutoConfiguration.class))
        .withBean(ProcessedEventRepository.class, () -> mock(ProcessedEventRepository.class))
        .withBean(TaskScheduler.class, () -> mock(TaskScheduler.class));

    @Test
    void providesTheProcessedEventService() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(ProcessedEventService.class));
    }

    @Test
    void prunesProcessedEventsByDefault() {
        contextRunner.run(context -> assertThat(context)
            .hasSingleBean(ProcessedEventPruner.class)
            .hasSingleBean(SchedulingConfigurer.class));
    }

    @Test
    void keepsTheServiceButSchedulesNothingWhenPruningIsDisabled() {
        contextRunner.withPropertyValues("app.idempotency.pruning.enabled=false").run(context -> assertThat(context)
            .hasSingleBean(ProcessedEventService.class)
            .doesNotHaveBean(ProcessedEventPruner.class)
            .doesNotHaveBean(SchedulingConfigurer.class));
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

    @Test
    void servicesWithoutIdempotencySettingsGetTheDefaults() {
        contextRunner.run(context -> {
            IdempotencyProperties.Pruning pruning = context.getBean(IdempotencyProperties.class).pruning();

            assertThat(pruning.enabled()).isTrue();
            assertThat(pruning.retention()).isEqualTo(Duration.ofDays(14));
            assertThat(pruning.interval()).isEqualTo(Duration.ofHours(1));
        });
    }

    @Test
    void servicesCanTuneRetentionAndInterval() {
        contextRunner
            .withPropertyValues("app.idempotency.pruning.retention=P30D", "app.idempotency.pruning.interval=PT6H")
            .run(context -> {
                IdempotencyProperties.Pruning pruning = context.getBean(IdempotencyProperties.class).pruning();

                assertThat(pruning.retention()).isEqualTo(Duration.ofDays(30));
                assertThat(pruning.interval()).isEqualTo(Duration.ofHours(6));
            });
    }
}
