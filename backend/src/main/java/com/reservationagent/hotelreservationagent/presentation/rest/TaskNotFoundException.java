package com.reservationagent.hotelreservationagent.presentation.rest;

import java.util.UUID;

/** Maps to {@code 404 Not Found}. */
public class TaskNotFoundException extends RuntimeException {

    public TaskNotFoundException(UUID taskId) {
        super("No task with id " + taskId);
    }
}
