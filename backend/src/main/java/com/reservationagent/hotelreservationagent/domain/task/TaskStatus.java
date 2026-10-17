package com.reservationagent.hotelreservationagent.domain.task;

import java.util.EnumSet;
import java.util.Set;

/** Workflow state machine for a booking task. */
public enum TaskStatus {
    RECEIVED,
    VALIDATING,
    DISCOVERING,
    RANKING,
    CALLING_FOR_QUOTES,
    OFFER_SELECTED,
    BOOKING,
    VERIFYING,
    EMAIL_PENDING,
    COMPLETED,

    INVALID_REQUEST,
    OUT_OF_SCOPE_LOCATION,
    NO_CANDIDATES,
    NO_VALID_OFFER,
    BOOKING_UNCERTAIN,
    BOOKING_FAILED,
    EMAIL_FAILED,
    DEADLINE_EXCEEDED;

    public boolean isTerminal() {
        return next().isEmpty();
    }

    public boolean canTransitionTo(TaskStatus target) {
        return next().contains(target) || (!isTerminal() && target == DEADLINE_EXCEEDED);
    }

    private Set<TaskStatus> next() {
        return switch (this) {
            case RECEIVED -> EnumSet.of(VALIDATING);
            case VALIDATING -> EnumSet.of(DISCOVERING, INVALID_REQUEST, OUT_OF_SCOPE_LOCATION);
            case DISCOVERING -> EnumSet.of(RANKING, NO_CANDIDATES);
            case RANKING -> EnumSet.of(CALLING_FOR_QUOTES, NO_CANDIDATES);
            case CALLING_FOR_QUOTES -> EnumSet.of(OFFER_SELECTED, NO_VALID_OFFER);
            // Enquiry-only tasks skip BOOKING and go straight to EMAIL_PENDING.
            case OFFER_SELECTED -> EnumSet.of(BOOKING, EMAIL_PENDING);
            case BOOKING -> EnumSet.of(VERIFYING, BOOKING_FAILED, BOOKING_UNCERTAIN);
            case VERIFYING -> EnumSet.of(EMAIL_PENDING, BOOKING_UNCERTAIN);
            // Failure states still owe the user an honest email.
            case NO_CANDIDATES, NO_VALID_OFFER, BOOKING_FAILED, BOOKING_UNCERTAIN -> EnumSet.of(EMAIL_PENDING);
            case EMAIL_PENDING -> EnumSet.of(COMPLETED, EMAIL_FAILED);
            case COMPLETED, INVALID_REQUEST, OUT_OF_SCOPE_LOCATION, EMAIL_FAILED, DEADLINE_EXCEEDED ->
                    EnumSet.noneOf(TaskStatus.class);
        };
    }
}
