package com.reservationagent.hotelreservationagent.domain.webhook;

import java.time.Instant;
import java.util.UUID;

/** An inbound provider event. {@code (provider, externalEventId)} is unique for idempotency. */
public record WebhookEvent(
        UUID id,
        WebhookProvider provider,
        String externalEventId,
        String eventType,
        String payload,
        Instant receivedAt,
        Instant processedAt,
        ProcessingStatus status
) {

    public enum ProcessingStatus { RECEIVED, PROCESSED, IGNORED_DUPLICATE, FAILED }
}
