package com.reservationagent.hotelreservationagent.domain.reservation;

/** Whether a real reservation exists. UNCERTAIN must be reconciled before any retry. */
public enum ReservationState {
    NOT_ATTEMPTED,
    ATTEMPT_IN_PROGRESS,
    CONFIRMED,
    FAILED,
    UNCERTAIN,
    CANCELLED
}
