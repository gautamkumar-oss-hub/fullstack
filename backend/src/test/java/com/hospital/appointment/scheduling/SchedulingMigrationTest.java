package com.hospital.appointment.scheduling;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class SchedulingMigrationTest {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.1-alpine");

    @Test
    void migratesAnEmptySchemaAndAnExistingV2IdentitySchema() throws Exception {
        Flyway empty = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()).initSql("SET TIME ZONE 'UTC'").load();
        empty.migrate();
        assertThat(countMigrations()).isEqualTo(3);
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA public CASCADE");
            statement.execute("CREATE SCHEMA public");
            statement.execute("GRANT ALL ON SCHEMA public TO public");
            statement.execute("GRANT ALL ON SCHEMA public TO " + POSTGRES.getUsername());
        }
        Flyway existing = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .target("2").initSql("SET TIME ZONE 'UTC'").load();
        existing.migrate();
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()).initSql("SET TIME ZONE 'UTC'").load().migrate();
        assertThat(countMigrations()).isEqualTo(3);
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT count(*) FROM pg_constraint WHERE conname IN ('doctor_weekly_no_overlap', 'doctor_override_no_overlap')")) {
            result.next();
            assertThat(result.getInt(1)).isEqualTo(2);
        }
    }

    private int countMigrations() throws Exception {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT count(*) FROM flyway_schema_history WHERE success")) {
            result.next();
            return result.getInt(1);
        }
    }
}
