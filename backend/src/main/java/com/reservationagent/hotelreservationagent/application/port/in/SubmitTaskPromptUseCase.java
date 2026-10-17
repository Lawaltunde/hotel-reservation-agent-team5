package com.reservationagent.hotelreservationagent.application.port.in;

import java.util.UUID;

/** Starts a task from one free-text request ("2 nights in Ottawa from next Friday, under $300, me@x.ca"). */
public interface SubmitTaskPromptUseCase {

    /**
     * Interprets the prompt, then validates and submits it exactly like {@link SubmitBookingTaskUseCase}.
     * Idempotent on {@code idempotencyKey} when one is given: a repeat returns the existing task
     * without interpreting the prompt again.
     */
    UUID submit(String prompt, String idempotencyKey);
}
