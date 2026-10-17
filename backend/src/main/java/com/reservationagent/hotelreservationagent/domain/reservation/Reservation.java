package com.reservationagent.hotelreservationagent.domain.reservation;

import com.reservationagent.hotelreservationagent.domain.offer.EvidenceStatus;
import com.reservationagent.hotelreservationagent.domain.shared.Money;

import java.time.Instant;
import java.util.UUID;

/** At most one per task, guarded by a unique constraint and the booking coordinator. */
public record Reservation(
        UUID id,
        UUID taskId,
        UUID hotelId,
        UUID offerId,
        String confirmationNumber,
        Money total,
        ReservationState state,
        EvidenceStatus evidence,
        Instant updatedAt
) {

    public boolean isConfirmed() {
        return state == ReservationState.CONFIRMED && confirmationNumber != null && !confirmationNumber.isBlank();
    }
}
