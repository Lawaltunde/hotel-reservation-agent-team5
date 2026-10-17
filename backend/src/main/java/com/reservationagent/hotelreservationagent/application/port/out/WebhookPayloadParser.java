package com.reservationagent.hotelreservationagent.application.port.out;

import com.reservationagent.hotelreservationagent.domain.transcript.TranscriptTurn;
import com.reservationagent.hotelreservationagent.domain.webhook.WebhookProvider;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Parses provider JSON into the minimal shapes the application needs. */
public interface WebhookPayloadParser {

    ParsedEvent parse(WebhookProvider provider, String rawBody);

    record ParsedEvent(String externalEventId, String eventType, Optional<CallCompleted> callCompleted,
                       Optional<EmailStatusChanged> emailStatusChanged) {
    }

    /**
     * @param endedAt         when the provider says the call ended, or {@code null} if it did not say
     * @param durationSeconds the billed call length, or 0 if unknown
     */
    record CallCompleted(String conversationId, List<TranscriptTurn> turns, boolean callSucceeded, String failureReason,
                         Instant endedAt, long durationSeconds) {
    }

    record EmailStatusChanged(String providerMessageId, String status) {
    }
}
