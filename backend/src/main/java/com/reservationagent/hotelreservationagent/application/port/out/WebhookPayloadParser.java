package com.reservationagent.hotelreservationagent.application.port.out;

import com.reservationagent.hotelreservationagent.domain.transcript.TranscriptTurn;
import com.reservationagent.hotelreservationagent.domain.webhook.WebhookProvider;

import java.util.List;
import java.util.Optional;

/** Parses provider JSON into the minimal shapes the application needs. */
public interface WebhookPayloadParser {

    ParsedEvent parse(WebhookProvider provider, String rawBody);

    record ParsedEvent(String externalEventId, String eventType, Optional<CallCompleted> callCompleted,
                       Optional<EmailStatusChanged> emailStatusChanged) {
    }

    record CallCompleted(String conversationId, List<TranscriptTurn> turns, boolean callSucceeded, String failureReason) {
    }

    record EmailStatusChanged(String providerMessageId, String status) {
    }
}
