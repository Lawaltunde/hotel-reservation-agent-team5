package com.reservationagent.hotelreservationagent.domain.shared;

/** Thrown when a domain invariant is violated. */
public class DomainException extends RuntimeException {

    public DomainException(String message) {
        super(message);
    }
}
