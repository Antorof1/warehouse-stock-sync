package com.github.antorof1.idempotency;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationInitializer;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jpa.autoconfigure.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@AutoConfiguration(
    before = {HibernateJpaAutoConfiguration.class, DataJpaRepositoriesAutoConfiguration.class},
    after = {DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class}
)
@AutoConfigurationPackage(basePackageClasses = ProcessedEvent.class)
public class IdempotencyAutoConfiguration {
    @Bean
    ProcessedEventService processedEventService(ProcessedEventRepository processedEventRepository) {
        return new ProcessedEventService(processedEventRepository);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "app.idempotency", name = "schema-migration", matchIfMissing = true)
    static class SchemaMigrationConfiguration {
        @Bean
        IdempotencySchemaMigration idempotencySchemaMigration(DataSource dataSource,
                                                              ObjectProvider<FlywayMigrationInitializer> serviceMigrations) {
            serviceMigrations.ifAvailable(initializer -> {});
            return new IdempotencySchemaMigration(dataSource);
        }

        @Bean
        static EntityManagerFactoryDependsOnPostProcessor idempotencySchemaMigrationBeforeJpa() {
            return new EntityManagerFactoryDependsOnPostProcessor(IdempotencySchemaMigration.class);
        }
    }
}
