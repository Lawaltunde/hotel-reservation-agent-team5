package com.reservationagent.hotelreservationagent.infrastructure.persistence.transcript;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transcripts")
class TranscriptEntity {

    @Id
    UUID id;
    UUID callId;
    /** One turn per line, prefixed "AGENT|" or "HOTEL|". */
    String body;
    Instant retainUntil;

    protected TranscriptEntity() {
    }
}
