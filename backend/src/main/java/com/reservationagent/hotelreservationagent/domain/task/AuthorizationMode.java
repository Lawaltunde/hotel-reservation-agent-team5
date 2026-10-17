package com.reservationagent.hotelreservationagent.domain.task;

/** What the agent is allowed to do on the user's behalf. */
public enum AuthorizationMode {
    /** Call, negotiate and report. Never commit a reservation. Safe default. */
    ENQUIRY_ONLY,
    /** Capture quotes and wait for explicit user approval before any booking. */
    REQUIRE_APPROVAL,
    /** Book once, only if the offer is within every user-approved limit. */
    BOOK_IF_WITHIN_LIMITS;

    public boolean allowsBooking() {
        return this == BOOK_IF_WITHIN_LIMITS;
    }
}
