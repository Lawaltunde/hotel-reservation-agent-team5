package com.reservationagent.hotelreservationagent.infrastructure.persistence.transcript;

import com.reservationagent.hotelreservationagent.application.port.out.TranscriptRepository;
import com.reservationagent.hotelreservationagent.domain.transcript.Transcript;
import com.reservationagent.hotelreservationagent.domain.transcript.TranscriptTurn;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
class TranscriptPersistenceAdapter implements TranscriptRepository {

    private final TranscriptJpaRepository jpa;

    TranscriptPersistenceAdapter(TranscriptJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Transcript save(Transcript t) {
        TranscriptEntity e = new TranscriptEntity();
        e.id = t.id();
        e.callId = t.callId();
        e.body = t.turns().stream()
                .map(turn -> turn.speaker() + "|" + turn.text().replace('\n', ' '))
                .collect(Collectors.joining("\n"));
        e.retainUntil = t.retainUntil();
        jpa.save(e);
        return t;
    }

    @Override
    public List<Transcript> findByTask(UUID taskId) {
        return jpa.findByTaskId(taskId).stream().map(TranscriptPersistenceAdapter::toDomain).toList();
    }

    private static Transcript toDomain(TranscriptEntity e) {
        List<TranscriptTurn> turns = e.body.isBlank() ? List.of() : Arrays.stream(e.body.split("\n"))
                .map(line -> line.split("\\|", 2))
                .map(parts -> new TranscriptTurn(TranscriptTurn.Speaker.valueOf(parts[0]),
                        parts.length > 1 ? parts[1] : "", Duration.ZERO))
                .toList();
        return new Transcript(e.id, e.callId, turns, e.retainUntil);
    }
}
