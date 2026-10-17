package com.reservationagent.hotelreservationagent.domain.notification;

import java.time.Instant;
import java.util.UUID;

/** The final report email for a task. */
public record EmailDelivery(
        UUID id,
        UUID taskId,
        String recipient,
        String providerMessageId,
        EmailStatus status,
        Instant sentAt,
        Instant deliveredAt,
        Instant bouncedAt
) {
}
