package com.reservationagent.hotelreservationagent.domain.notification;

/** ACCEPTED means the provider took the message; it does not prove delivery. */
public enum EmailStatus {
    PENDING,
    ACCEPTED,
    DELIVERED,
    DELAYED,
    BOUNCED,
    FAILED
}
