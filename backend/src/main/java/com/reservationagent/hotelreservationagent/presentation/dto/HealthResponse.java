package com.reservationagent.hotelreservationagent.presentation.dto;

import java.time.Instant;

/** {@code status} is UP only when the database answers too; {@code database} is UP or DOWN. */
public record HealthResponse(String status, String service, Instant time, String database) {
}
