package com.reservationagent.hotelreservationagent.domain.task;

/** What the user is told happened. A quote is never reported as a booking. */
public enum TaskOutcome {
    ENQUIRY_COMPLETED,
    QUOTE_RECEIVED,
    BOOKING_CONFIRMED,
    NO_ELIGIBLE_HOTELS,
    NO_VALID_QUOTE,
    FOLLOW_UP_REQUIRED,
    CALL_FAILED,
    BOOKING_UNCERTAIN,
    EMAIL_FAILED,
    DEADLINE_EXCEEDED
}
