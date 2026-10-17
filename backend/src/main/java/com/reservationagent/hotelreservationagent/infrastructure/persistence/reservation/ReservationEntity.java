package com.reservationagent.hotelreservationagent.infrastructure.persistence.reservation;

import com.reservationagent.hotelreservationagent.domain.offer.EvidenceStatus;
import com.reservationagent.hotelreservationagent.domain.reservation.ReservationState;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reservations")
class ReservationEntity {

    @Id
    UUID id;
    UUID taskId;
    UUID hotelId;
    UUID offerId;
    String confirmationNumber;
    BigDecimal total;
    @Enumerated(EnumType.STRING)
    ReservationState state;
    @Enumerated(EnumType.STRING)
    EvidenceStatus evidence;
    Instant updatedAt;

    protected ReservationEntity() {
    }
}
