package com.reservationagent.hotelreservationagent.application.port.in;

import com.reservationagent.hotelreservationagent.application.event.TaskProgressEvent;

import java.util.UUID;
import java.util.function.Consumer;

public interface SubscribeTaskProgressUseCase {

    /** @return a handle that removes the listener when closed */
    AutoCloseable subscribe(UUID taskId, Consumer<TaskProgressEvent> listener);
}
