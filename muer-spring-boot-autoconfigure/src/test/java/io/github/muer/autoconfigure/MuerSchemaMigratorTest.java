package io.github.muer.autoconfigure;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MuerSchemaMigratorTest {
    @Test
    void migrates_only_the_namespaced_iam_location_with_its_own_history_table() {
        var dataSource = mock(DataSource.class);
        var configuration = mock(FluentConfiguration.class);
        var flyway = mock(Flyway.class);
        when(configuration.dataSource(dataSource)).thenReturn(configuration);
        when(configuration.locations("classpath:db/iam/migration")).thenReturn(configuration);
        when(configuration.table("iam_flyway_schema_history")).thenReturn(configuration);
        when(configuration.load()).thenReturn(flyway);

        new MuerSchemaMigrator(dataSource, "iam_flyway_schema_history", () -> configuration).migrate();

        verify(configuration).dataSource(dataSource);
        verify(configuration).locations("classpath:db/iam/migration");
        verify(configuration).table("iam_flyway_schema_history");
        verify(flyway).migrate();
    }

    @Test
    void rejects_an_unsafe_history_table_identifier() {
        assertThrows(IllegalArgumentException.class,
                () -> new MuerSchemaMigrator(mock(DataSource.class), "history; DROP TABLE account"));
    }
}
