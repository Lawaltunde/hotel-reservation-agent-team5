package com.reservationagent.hotelreservationagent.infrastructure.persistence.recording;

import com.reservationagent.hotelreservationagent.application.port.out.RecordingLinkRepository;
import com.reservationagent.hotelreservationagent.domain.call.RecordingLink;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
class RecordingLinkPersistenceAdapter implements RecordingLinkRepository {

    private final RecordingLinkJpaRepository jpa;

    RecordingLinkPersistenceAdapter(RecordingLinkJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public RecordingLink save(RecordingLink link) {
        RecordingLinkEntity e = new RecordingLinkEntity();
        e.tokenHash = link.tokenHash();
        e.callId = link.callId();
        e.conversationId = link.conversationId();
        e.expiresAt = link.expiresAt();
        e.createdAt = link.createdAt();
        jpa.save(e);
        return link;
    }

    @Override
    public Optional<RecordingLink> findByTokenHash(String tokenHash) {
        return jpa.findById(tokenHash)
                .map(e -> new RecordingLink(e.tokenHash, e.callId, e.conversationId, e.expiresAt, e.createdAt));
    }
}
