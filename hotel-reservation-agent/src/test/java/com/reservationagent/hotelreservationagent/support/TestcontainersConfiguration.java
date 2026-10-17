package com.reservationagent.hotelreservationagent.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared Testcontainers setup: a PostgreSQL + PostGIS container wired into the
 * Spring context through {@link ServiceConnection} (no datasource properties needed).
 *
 * <p>Uses {@code imresamu/postgis} (multi-arch amd64 + arm64, same PostgreSQL 17 / PostGIS 3.5
 * as {@code postgis/postgis:17-3.5} in {@code compose.yaml}) so it runs natively on Apple Silicon.
 * Keep the PostgreSQL/PostGIS versions in step with {@code compose.yaml}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    public static final DockerImageName POSTGIS_IMAGE =
            DockerImageName.parse("imresamu/postgis:17-3.5").asCompatibleSubstituteFor("postgres");

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgisContainer() {
        return new PostgreSQLContainer(POSTGIS_IMAGE);
    }
}
