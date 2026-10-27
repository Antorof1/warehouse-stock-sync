package com.github.antorof1.idempotency;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.InitializingBean;

import javax.sql.DataSource;

public class IdempotencySchemaMigration implements InitializingBean {
    static final String LOCATION = "classpath:db/idempotency";
    static final String HISTORY_TABLE = "flyway_schema_history_idempotency";

    private final DataSource dataSource;

    public IdempotencySchemaMigration(DataSource dataSource) {
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
