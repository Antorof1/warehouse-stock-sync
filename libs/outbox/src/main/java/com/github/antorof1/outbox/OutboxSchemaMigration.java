package com.github.antorof1.outbox;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.InitializingBean;

import javax.sql.DataSource;

public class OutboxSchemaMigration implements InitializingBean {
    static final String LOCATION = "classpath:db/outbox";
    static final String HISTORY_TABLE = "flyway_schema_history_outbox";

    private final DataSource dataSource;

    public OutboxSchemaMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void afterPropertiesSet() {
        Flyway.configure()
            .dataSource(dataSource)
            .locations(LOCATION)
            .table(HISTORY_TABLE)
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .load()
            .migrate();
    }
}
