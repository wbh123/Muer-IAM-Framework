package io.github.iamstarter.autoconfigure;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;

import javax.sql.DataSource;
import java.util.Objects;
import java.util.function.Supplier;

public final class IamSchemaMigrator {
    static final String MIGRATION_LOCATION = "classpath:db/iam/migration";

    private final DataSource dataSource;
    private final String historyTable;
    private final Supplier<FluentConfiguration> configurations;

    public IamSchemaMigrator(DataSource dataSource, String historyTable) {
        this(dataSource, historyTable, Flyway::configure);
    }

    IamSchemaMigrator(DataSource dataSource, String historyTable,
                      Supplier<FluentConfiguration> configurations) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        if (historyTable == null || !historyTable.matches("[A-Za-z][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("historyTable must be a simple SQL identifier");
        }
        this.historyTable = historyTable;
        this.configurations = Objects.requireNonNull(configurations, "configurations must not be null");
    }

    public void migrate() {
        configurations.get()
                .dataSource(dataSource)
                .locations(MIGRATION_LOCATION)
                .table(historyTable)
                .load()
                .migrate();
    }
}
