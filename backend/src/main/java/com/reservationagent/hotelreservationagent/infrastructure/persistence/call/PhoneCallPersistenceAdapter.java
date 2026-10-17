package com.reservationagent.hotelreservationagent.infrastructure.persistence.call;

import com.reservationagent.hotelreservationagent.application.port.out.PhoneCallRepository;
import com.reservationagent.hotelreservationagent.domain.call.CallStatus;
import com.reservationagent.hotelreservationagent.domain.call.PhoneCall;
import com.reservationagent.hotelreservationagent.domain.hotel.PhoneNumber;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class PhoneCallPersistenceAdapter implements PhoneCallRepository {

    private static final EnumSet<CallStatus> ACTIVE =
            EnumSet.of(CallStatus.QUEUED, CallStatus.INITIATED, CallStatus.RINGING, CallStatus.IN_PROGRESS);

    private final PhoneCallJpaRepository jpa;

    PhoneCallPersistenceAdapter(PhoneCallJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public PhoneCall save(PhoneCall c) {
        PhoneCallEntity e = jpa.findById(c.id()).orElseGet(PhoneCallEntity::new);
        e.id = c.id();
        e.taskId = c.taskId();
        e.hotelId = c.hotelId();
        e.dialledNumber = c.dialledNumber().e164();
        e.providerCallId = c.providerCallId();
        e.conversationId = c.conversationId();
        e.status = c.status();
        e.outcome = c.outcome();
        e.startedAt = c.startedAt();
        e.endedAt = c.endedAt();
        e.failureReason = c.failureReason();
        return toDomain(jpa.save(e));
    }

    @Override
    public Optional<PhoneCall> findByConversationId(String conversationId) {
        return jpa.findByConversationId(conversationId).map(PhoneCallPersistenceAdapter::toDomain);
    }

    @Override
    public List<PhoneCall> findByTask(UUID taskId) {
        return jpa.findByTaskId(taskId).stream().map(PhoneCallPersistenceAdapter::toDomain).toList();
    }

    @Override
    public int countActive(UUID taskId) {
        return jpa.countByTaskIdAndStatusIn(taskId, ACTIVE);
    }

    private static PhoneCall toDomain(PhoneCallEntity e) {
        return new PhoneCall(e.id, e.taskId, e.hotelId, new PhoneNumber(e.dialledNumber), e.providerCallId,
                e.conversationId, e.status, e.outcome, e.startedAt, e.endedAt, e.failureReason);
    }
}
