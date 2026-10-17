package com.reservationagent.hotelreservationagent.application.service.validation;

import java.util.List;
import java.util.stream.Collectors;

/** The request is missing or has invalid essential data. Maps to HTTP 400. */
public class RequestValidationException extends RuntimeException {

    private final List<FieldError> errors;

    public RequestValidationException(List<FieldError> errors) {
        super(errors.stream().map(FieldError::toString).collect(Collectors.joining("; ")));
        this.errors = List.copyOf(errors);
    }

    public List<FieldError> errors() {
        return errors;
    }

    /**
     * One problem with the request.
     *
     * @param field   the request field it concerns, or {@code null} if it concerns the whole request
     * @param message a human-readable explanation that does not repeat the field's JSON name
     */
    public record FieldError(String field, String message) {

        @Override
        public String toString() {
            return field == null ? message : field + ": " + message;
        }
    }
}
