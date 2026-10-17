package com.reservationagent.hotelreservationagent.domain.candidate;

/** Why a hotel was not called. Reported to the user for transparency. */
public enum ExclusionReason {
    NO_PHONE_NUMBER,
    NOT_OPERATIONAL,
    PROPERTY_TYPE_NOT_ALLOWED,
    OUTSIDE_SEARCH_AREA,
    OVER_BUDGET,
    MISSING_REQUIRED_AMENITY,
    DUPLICATE,
    DEADLINE_REACHED,
    NOT_REACHED_IN_RANKING
}
