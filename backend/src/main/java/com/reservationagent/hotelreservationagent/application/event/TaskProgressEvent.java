package com.reservationagent.hotelreservationagent.application.event;

import com.reservationagent.hotelreservationagent.domain.task.TaskStatus;

import java.time.Instant;
import java.util.UUID;

/** Pushed to the frontend over server-sent events as the task advances. */
public record TaskProgressEvent(UUID taskId, TaskStatus status, String message, Instant at) {
}
