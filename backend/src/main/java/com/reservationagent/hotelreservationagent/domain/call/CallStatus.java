package com.reservationagent.hotelreservationagent.domain.call;

/** Telephony lifecycle of one call. */
public enum CallStatus {
    QUEUED,
    INITIATED,
    RINGING,
    IN_PROGRESS,
    COMPLETED,
    NO_ANSWER,
    BUSY,
    FAILED,
    CANCELLED
}
