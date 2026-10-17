package com.reservationagent.hotelreservationagent.domain.task;

import com.reservationagent.hotelreservationagent.domain.shared.Money;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * How a task's request was obtained and what the user actually said. The stay on the task always
 * has concrete values so hotels can be asked; {@code stated} says which of them came from the user,
 * and the voice agent and the UI treat the rest as unspecified.
 *
 * @param originalPrompt     the free-text request, or {@code null} for a form submission
 * @param stated             the fields the user gave
 * @param emailSource        where the report address came from
 * @param notes              free-text preferences from the prompt (max {@value #MAX_NOTES_LENGTH} chars), or {@code null}
 * @param maximumPerNight    the nightly budget, only when the user put it that way
 * @param assumptions        human-readable notes on what was assumed for the search
 */
public record TaskIntake(String originalPrompt, Set<StatedField> stated, EmailSource emailSource, String notes,
                         Money maximumPerNight, List<String> assumptions) {

    public static final int MAX_NOTES_LENGTH = 300;

    /** A form submission: every field was given explicitly. */
    public static final TaskIntake FORM = new TaskIntake(null, EnumSet.allOf(StatedField.class), EmailSource.STATED,
            null, null, List.of());

    public TaskIntake {
        stated = stated == null || stated.isEmpty() ? EnumSet.noneOf(StatedField.class) : EnumSet.copyOf(stated);
        emailSource = emailSource == null ? EmailSource.STATED : emailSource;
        notes = notes == null || notes.isBlank() ? null
                : notes.strip().length() > MAX_NOTES_LENGTH ? notes.strip().substring(0, MAX_NOTES_LENGTH) : notes.strip();
        assumptions = assumptions == null ? List.of() : List.copyOf(assumptions);
    }

    public boolean isStated(StatedField field) {
        return stated.contains(field);
    }

    public enum StatedField {
        CHECK_IN, CHECK_OUT, ADULTS, CHILDREN, ROOMS, MAX_BUDGET, EMAIL
    }

    public enum EmailSource {
        /** The user gave the address. */
        STATED,
        /** The prompt had none; the report goes to the configured organizer inbox. */
        ORGANIZER_FALLBACK
    }
}
