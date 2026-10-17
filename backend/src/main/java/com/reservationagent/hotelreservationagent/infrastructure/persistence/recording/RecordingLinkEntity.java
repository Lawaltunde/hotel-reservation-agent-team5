package com.reservationagent.hotelreservationagent.infrastructure.persistence.recording;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "call_recording_links")
class RecordingLinkEntity {

    @Id
    String tokenHash;
    UUID callId;
    String conversationId;
    Instant expiresAt;
    Instant createdAt;

    protected RecordingLinkEntity() {
    }
}
