package com.reservationagent.hotelreservationagent.presentation.dto;

import com.reservationagent.hotelreservationagent.application.event.TaskProgressEvent;
import com.reservationagent.hotelreservationagent.domain.task.TaskStatus;
import java.time.Instant;
import java.util.UUID;

/** Data of one {@code progress} server-sent event. */
public record ProgressEventResponse(UUID taskId, TaskStatus status, String message, Instant at) {

    public static ProgressEventResponse from(TaskProgressEvent event) {
        return new ProgressEventResponse(event.taskId(), event.status(), event.message(), event.at());
    }
}
