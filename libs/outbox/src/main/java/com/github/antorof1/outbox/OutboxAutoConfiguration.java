package com.github.antorof1.outbox;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationInitializer;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jpa.autoconfigure.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;

@AutoConfiguration(
    before = {HibernateJpaAutoConfiguration.class, DataJpaRepositoriesAutoConfiguration.class},
    after = {KafkaAutoConfiguration.class, DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class}
)
@AutoConfigurationPackage(basePackageClasses = OutboxEvent.class)
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxAutoConfiguration {
    @Bean
    OutboxEventService outboxEventService(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper,
                                          KafkaProperties kafkaProperties) {
        return new OutboxEventService(outboxEventRepository, objectMapper, kafkaProperties);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "app.outbox", name = "schema-migration", matchIfMissing = true)
    static class SchemaMigrationConfiguration {
        @Bean
        OutboxSchemaMigration outboxSchemaMigration(DataSource dataSource,
                                                    ObjectProvider<FlywayMigrationInitializer> serviceMigrations) {
            serviceMigrations.ifAvailable(initializer -> {});
            return new OutboxSchemaMigration(dataSource);
        }

        @Bean
        static EntityManagerFactoryDependsOnPostProcessor outboxSchemaMigrationBeforeJpa() {
            return new EntityManagerFactoryDependsOnPostProcessor(OutboxSchemaMigration.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "app.outbox", name = "enabled", matchIfMissing = true)
    @EnableScheduling
    @EnableSchedulerLock(defaultLockAtMostFor = "PT5M")
    static class PublishingConfiguration {
        @Bean
        OutboxPublisher outboxPublisher(OutboxEventRepository outboxEventRepository,
                                        KafkaTemplate<String, String> kafkaTemplate,
                                        OutboxProperties outboxProperties) {
            return new OutboxPublisher(outboxEventRepository, kafkaTemplate, outboxProperties);
        }

        @Bean
        OutboxSweeper outboxSweeper(OutboxEventRepository outboxEventRepository, OutboxProperties outboxProperties) {
            return new OutboxSweeper(outboxEventRepository, outboxProperties);
        }

        @Bean
        SchedulingConfigurer outboxScheduling(OutboxPublisher outboxPublisher, OutboxSweeper outboxSweeper,
                                              OutboxProperties outboxProperties) {
            return register -> {
                register.addFixedDelayTask(outboxPublisher::poll, outboxProperties.pollInterval());
                register.addFixedDelayTask(outboxSweeper::run, outboxProperties.sweepInterval());
            };
        }

        @Bean
        @ConditionalOnBean(DataSource.class)
        @ConditionalOnMissingBean(LockProvider.class)
        LockProvider lockProvider(DataSource dataSource) {
            return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                    .withJdbcTemplate(new JdbcTemplate(dataSource))
                    .withTableName("outbox_shedlock")
                    .usingDbTime()
                    .build()
            );
        }
    }
}
