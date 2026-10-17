package com.reservationagent.hotelreservationagent.infrastructure.persistence.call;

import com.reservationagent.hotelreservationagent.domain.call.CallOutcome;
import com.reservationagent.hotelreservationagent.domain.call.CallStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "phone_calls")
class PhoneCallEntity {

    @Id
    UUID id;
    UUID taskId;
    UUID hotelId;
    String dialledNumber;
    String providerCallId;
    String conversationId;
    @Enumerated(EnumType.STRING)
    CallStatus status;
    @Enumerated(EnumType.STRING)
    CallOutcome outcome;
    Instant startedAt;
    Instant endedAt;
    String failureReason;

    protected PhoneCallEntity() {
    }
}
