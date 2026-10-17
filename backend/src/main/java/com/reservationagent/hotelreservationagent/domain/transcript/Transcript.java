package com.reservationagent.hotelreservationagent.domain.transcript;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Conversation transcript for a call, retained until {@code retainUntil}. */
public record Transcript(UUID id, UUID callId, List<TranscriptTurn> turns, Instant retainUntil) {

    public Transcript {
        turns = turns == null ? List.of() : List.copyOf(turns);
    }

    public String asPlainText() {
        return turns.stream()
                .map(t -> (t.speaker() == TranscriptTurn.Speaker.AGENT ? "Agent: " : "Hotel: ") + t.text())
                .collect(Collectors.joining("\n"));
    }
}
