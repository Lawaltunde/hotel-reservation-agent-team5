package com.reservationagent.hotelreservationagent.infrastructure.persistence;

import com.reservationagent.hotelreservationagent.application.port.out.DatabaseHealth;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * {@code SELECT 1} with a 2 s cap: the pool would otherwise wait its full connection timeout (30 s)
 * when the database is gone, and the health check must answer quickly.
 */
@Component
class JdbcDatabaseHealth implements DatabaseHealth {

    private static final long TIMEOUT_MS = 2000;

    private final JdbcClient jdbc;
    private final Executor executor = Executors.newVirtualThreadPerTaskExecutor();

    JdbcDatabaseHealth(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isUp() {
        try {
            return CompletableFuture.supplyAsync(() -> jdbc.sql("SELECT 1").query(Integer.class).single() == 1, executor)
                    .completeOnTimeout(false, TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .exceptionally(e -> false)
                    .join();
        } catch (RuntimeException e) {
            return false;
        }
    }
}
