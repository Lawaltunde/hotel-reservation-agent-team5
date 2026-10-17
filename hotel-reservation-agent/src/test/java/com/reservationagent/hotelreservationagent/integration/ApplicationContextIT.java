package com.reservationagent.hotelreservationagent.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.reservationagent.hotelreservationagent.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Boots the full Spring context against a real PostGIS container.
 * If Flyway is on the classpath, its migrations run during startup; this test then
 * checks that every recorded migration succeeded.
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
    void flywayMigrationsAppliedSuccessfullyWhenPresent() {
        Boolean historyExists = jdbc.queryForObject(
                "SELECT to_regclass('public.flyway_schema_history') IS NOT NULL", Boolean.class);
        if (!Boolean.TRUE.equals(historyExists)) {
            return; // Flyway not wired in yet; nothing to check.
        }
        Integer failed = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE NOT success", Integer.class);
        assertThat(failed).as("failed Flyway migrations").isZero();
    }
}
