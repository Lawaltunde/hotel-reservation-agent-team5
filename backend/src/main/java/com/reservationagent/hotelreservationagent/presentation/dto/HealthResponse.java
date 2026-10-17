package com.reservationagent.hotelreservationagent.presentation.dto;

import java.time.Instant;

public record HealthResponse(String status, String service, Instant time) {
}
