package com.reservationagent.hotelreservationagent.domain.call;

import com.reservationagent.hotelreservationagent.domain.hotel.PhoneNumber;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** One outbound call to one hotel for one task. */
public record PhoneCall(
        UUID id,
        UUID taskId,
        UUID hotelId,
        PhoneNumber dialledNumber,
        String providerCallId,
        String conversationId,
        CallStatus status,
        CallOutcome outcome,
        Instant startedAt,
        Instant endedAt,
        String failureReason
) {

    public Duration duration() {
        return startedAt == null || endedAt == null ? Duration.ZERO : Duration.between(startedAt, endedAt);
    }
}
