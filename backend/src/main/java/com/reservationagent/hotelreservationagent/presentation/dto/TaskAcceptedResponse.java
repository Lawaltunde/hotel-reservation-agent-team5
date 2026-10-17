package com.reservationagent.hotelreservationagent.presentation.dto;

import com.reservationagent.hotelreservationagent.domain.task.TaskStatus;
import java.util.UUID;

/** {@code 202 Accepted} body of {@code POST /api/tasks}. */
public record TaskAcceptedResponse(UUID taskId, TaskStatus status) {
}
