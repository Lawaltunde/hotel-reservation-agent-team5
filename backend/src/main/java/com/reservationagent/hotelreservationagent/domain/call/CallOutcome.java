package com.reservationagent.hotelreservationagent.domain.call;

/** What the conversation achieved, as extracted from the transcript. */
public enum CallOutcome {
    OFFER_RECEIVED,
    NO_AVAILABILITY,
    TOO_EXPENSIVE,
    VOICEMAIL,
    IVR_FAILED,
    HOLD_TIMEOUT,
    WRONG_NUMBER,
    REFUSED_AI_CALLER,
    CALL_DROPPED,
    UNKNOWN
}
