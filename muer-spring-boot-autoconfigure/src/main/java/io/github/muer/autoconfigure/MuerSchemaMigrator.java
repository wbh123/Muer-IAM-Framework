package io.github.muer.autoconfigure;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;

import javax.sql.DataSource;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 执行 Muer 所需数据库结构的初始化迁移。
 *
 * <p>迁移继续使用既有物理表和历史表名称，以保证已部署实例可以平滑升级。</p>
 */
public final class MuerSchemaMigrator {
    static final String MIGRATION_LOCATION = "classpath:db/iam/migration";

    private final DataSource dataSource;
    private final String historyTable;
    private final Supplier<FluentConfiguration> configurations;

    /**
     * 创建使用 Flyway 默认配置入口的迁移器。
     *
     * @param dataSource 数据源
     * @param historyTable 既有 Flyway 历史表名称
     */
    public MuerSchemaMigrator(DataSource dataSource, String historyTable) {
        this(dataSource, historyTable, Flyway::configure);
    }

    MuerSchemaMigrator(DataSource dataSource, String historyTable,
                      Supplier<FluentConfiguration> configurations) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        if (historyTable == null || !historyTable.matches("[A-Za-z][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("historyTable must be a simple SQL identifier");
        }
        this.historyTable = historyTable;
        this.configurations = Objects.requireNonNull(configurations, "configurations must not be null");
    }

    /**
     * 在数据源可用时执行既有 SQL 迁移集合。
     */
    public void migrate() {
        configurations.get()
                .dataSource(dataSource)
                .locations(MIGRATION_LOCATION)
                .table(historyTable)
                .load()
                .migrate();
    }
}
