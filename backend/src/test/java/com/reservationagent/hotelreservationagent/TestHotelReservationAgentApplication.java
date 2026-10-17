package com.reservationagent.hotelreservationagent;

import com.reservationagent.hotelreservationagent.support.TestcontainersConfiguration;
import org.springframework.boot.SpringApplication;

/**
 * Runs the application locally against a throwaway PostGIS container.
 * Start from the IDE, or with {@code ./mvnw spring-boot:test-run}.
 */
public class TestHotelReservationAgentApplication {

    public static void main(String[] args) {
        SpringApplication.from(HotelReservationAgentApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
