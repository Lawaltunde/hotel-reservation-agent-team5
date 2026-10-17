package com.reservationagent.hotelreservationagent.application.port.out;

import com.reservationagent.hotelreservationagent.domain.task.DateExpression;

import java.math.BigDecimal;
import java.util.List;

/**
 * Reads a free-text booking request into structured fields (Spring AI). The output is untrusted and
 * goes through the same validation as a form submission. Dates are returned as
 * {@link DateExpression}s and resolved in Java; email addresses are removed before the prompt is sent.
 */
public interface TaskPromptInterpreter {

    /**
     * @throws InterpreterUnavailableException if the AI provider cannot be reached or returns nothing
     */
    InterpretedPrompt interpret(String redactedPrompt);

    /**
     * Every field is {@code null} when the prompt did not say it.
     *
     * @param nights stay length, when given instead of (or as well as) a check-out date
     * @param notes  other preferences in the guest's words (quiet room, near downtown...), or {@code null}
     */
    record InterpretedPrompt(
            String city,
            DateExpression checkIn,
            DateExpression checkOut,
            Integer nights,
            Integer rooms,
            Integer adults,
            Integer children,
            Budget budget,
            List<String> amenities,
            String notes
    ) {
    }

    /** A budget as the guest put it; Java turns it into a stay total. */
    record Budget(BigDecimal amount, BudgetUnit unit) {
    }

    enum BudgetUnit {
        PER_NIGHT, PER_STAY, UNKNOWN
    }

    class InterpreterUnavailableException extends RuntimeException {
        public InterpreterUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
