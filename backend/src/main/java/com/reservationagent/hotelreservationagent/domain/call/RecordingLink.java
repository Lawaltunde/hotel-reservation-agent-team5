package com.reservationagent.hotelreservationagent.domain.call;

import java.time.Instant;
import java.util.UUID;

/**
 * Grants access to one call's audio through an unguessable link. Only the SHA-256 hash of the
 * token is stored, so a database leak does not reveal working links.
 */
public record RecordingLink(String tokenHash, UUID callId, String conversationId, Instant expiresAt, Instant createdAt) {

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
