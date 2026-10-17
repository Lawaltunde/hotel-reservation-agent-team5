package com.reservationagent.hotelreservationagent.application.service.validation;

import java.util.List;

/** The request is missing or has invalid essential data. Maps to HTTP 400. */
public class RequestValidationException extends RuntimeException {

    private final List<String> errors;

    public RequestValidationException(List<String> errors) {
        super(String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }
}
