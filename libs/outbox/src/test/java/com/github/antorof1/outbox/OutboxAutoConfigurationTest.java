package com.github.antorof1.outbox;

import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.SchedulingConfigurer;

import javax.sql.DataSource;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OutboxAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            OutboxAutoConfiguration.class, KafkaAutoConfiguration.class, JacksonAutoConfiguration.class
        ))
        .withBean(OutboxEventRepository.class, () -> mock(OutboxEventRepository.class));

    @Test
    void recordsAndPublishesByDefault() {
        contextRunner.run(context -> assertThat(context)
            .hasSingleBean(OutboxEventService.class)
            .hasSingleBean(OutboxMetrics.class)
            .hasSingleBean(OutboxPublisher.class)
            .hasSingleBean(OutboxSweeper.class)
            .hasSingleBean(SchedulingConfigurer.class));
    }

    @Test
    void keepsRecordingButSchedulesNothingWhenDisabled() {
        contextRunner.withPropertyValues("app.outbox.enabled=false").run(context -> assertThat(context)
            .hasSingleBean(OutboxEventService.class)
            .hasSingleBean(OutboxMetrics.class)
            .doesNotHaveBean(OutboxPublisher.class)
            .doesNotHaveBean(OutboxSweeper.class)
            .doesNotHaveBean(SchedulingConfigurer.class));
    }

    @Test
    void locksPublishingThroughTheDataSource() {
        contextRunner
            .withBean(DataSource.class, () -> mock(DataSource.class))
            .withPropertyValues("app.outbox.schema-migration=false")
            .run(context -> assertThat(context).hasSingleBean(LockProvider.class));
    }

    @Test
    void backsOffWhenServiceProvidesItsOwnLockProvider() {
        LockProvider serviceLockProvider = mock(LockProvider.class);

        contextRunner
            .withBean(DataSource.class, () -> mock(DataSource.class))
            .withBean(LockProvider.class, () -> serviceLockProvider)
            .withPropertyValues("app.outbox.schema-migration=false")
            .run(context -> assertThat(context.getBean(LockProvider.class)).isSameAs(serviceLockProvider));
    }

    @Test
    void skipsSchemaMigrationWithoutDataSource() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(OutboxSchemaMigration.class));
    }

    @Test
    void skipsSchemaMigrationWhenServiceOptsOut() {
        contextRunner
            .withBean(DataSource.class, () -> mock(DataSource.class))
            .withPropertyValues("app.outbox.schema-migration=false")
            .run(context -> assertThat(context).doesNotHaveBean(OutboxSchemaMigration.class));
    }

    @Test
    void servicesWithoutOutboxSettingsGetTheDefaults() {
        contextRunner.run(context -> {
            OutboxProperties properties = context.getBean(OutboxProperties.class);

            assertThat(properties.enabled()).isTrue();
            assertThat(properties.pollInterval()).isEqualTo(Duration.ofMillis(500));
            assertThat(properties.batchSize()).isEqualTo(100);
            assertThat(properties.retention()).isEqualTo(Duration.ofDays(7));
            assertThat(properties.sweepInterval()).isEqualTo(Duration.ofHours(1));
        });
    }
}
