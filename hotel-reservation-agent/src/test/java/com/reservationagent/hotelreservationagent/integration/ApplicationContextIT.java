package com.reservationagent.hotelreservationagent.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.reservationagent.hotelreservationagent.support.TestcontainersConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Boots the full Spring context against a real PostGIS container.
 * Flyway migrations run during startup; these tests check that they all succeeded
 * and that V1 installed the required extensions.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ApplicationContextIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void contextLoadsAgainstPostgis() {
        Integer one = jdbc.queryForObject("SELECT 1", Integer.class);
        assertThat(one).isEqualTo(1);
    }

    @Test
    void databaseImageProvidesPostgisExtension() {
        Integer available = jdbc.queryForObject(
                "SELECT count(*) FROM pg_available_extensions WHERE name = 'postgis'", Integer.class);
        assertThat(available).isEqualTo(1);
    }

    @Test
    void flywayMigrationsAppliedSuccessfully() {
        Integer applied = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success AND version IS NOT NULL",
                Integer.class);
        Integer failed = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE NOT success", Integer.class);
        assertThat(applied).as("applied Flyway migrations").isPositive();
        assertThat(failed).as("failed Flyway migrations").isZero();
    }

    @Test
    void v1MigrationInstalledPostgisAndUnaccent() {
        List<String> installed = jdbc.queryForList(
                "SELECT extname FROM pg_extension WHERE extname IN ('postgis', 'unaccent')", String.class);
        assertThat(installed).containsExactlyInAnyOrder("postgis", "unaccent");
        String postgisVersion = jdbc.queryForObject("SELECT postgis_lib_version()", String.class);
        assertThat(postgisVersion).startsWith("3.5");
    }
}
