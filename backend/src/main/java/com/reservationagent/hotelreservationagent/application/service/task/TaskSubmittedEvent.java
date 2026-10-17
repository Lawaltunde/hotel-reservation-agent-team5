package com.reservationagent.hotelreservationagent.application.service.task;

import java.util.UUID;

/** Published after a new task is committed; starts the orchestrator asynchronously. */
public record TaskSubmittedEvent(UUID taskId) {
}
