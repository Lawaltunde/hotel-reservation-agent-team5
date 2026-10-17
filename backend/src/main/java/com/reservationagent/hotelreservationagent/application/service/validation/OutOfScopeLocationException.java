package com.reservationagent.hotelreservationagent.application.service.validation;

/** The destination is not a Canadian municipality we can find, or is ambiguous. Maps to HTTP 422. */
public class OutOfScopeLocationException extends RuntimeException {

    private final java.util.List<String> suggestions;

    public OutOfScopeLocationException(String message, java.util.List<String> suggestions) {
        super(message);
        this.suggestions = java.util.List.copyOf(suggestions);
    }

    public java.util.List<String> suggestions() {
        return suggestions;
    }
}
