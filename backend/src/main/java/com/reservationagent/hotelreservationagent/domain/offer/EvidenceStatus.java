package com.reservationagent.hotelreservationagent.domain.offer;

/** Strength of the evidence behind an offer or reservation. */
public enum EvidenceStatus {
    VERBAL_ONLY,
    REFERENCE_ID_GIVEN,
    WRITTEN_CONFIRMATION_PROMISED,
    WRITTEN_CONFIRMATION_RECEIVED
}
