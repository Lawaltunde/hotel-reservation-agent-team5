package com.reservationagent.hotelreservationagent.application.port.in;

import com.reservationagent.hotelreservationagent.domain.task.AuthorizationMode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public interface SubmitBookingTaskUseCase {

    /** Idempotent on {@code requestId}: resubmitting returns the existing task. */
    UUID submit(Command command);

    record Command(
            String requestId,
            String city,
            LocalDate checkIn,
            LocalDate checkOut,
            int rooms,
            int adults,
            int children,
            String destinationEmail,
            Instant deadline,
            Set<String> requiredAmenities,
            AuthorizationMode authorizationMode,
            BigDecimal maximumTotalCad,
            boolean allowCompetingQuoteDisclosure,
            String originalPrompt
    ) {
    }
}
