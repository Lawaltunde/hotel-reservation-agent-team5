package com.reservationagent.hotelreservationagent.application.service.progress;

import com.reservationagent.hotelreservationagent.application.event.TaskProgressEvent;
import com.reservationagent.hotelreservationagent.application.port.in.SubscribeTaskProgressUseCase;
import com.reservationagent.hotelreservationagent.application.port.out.TaskEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** In-process fan-out of progress events to SSE subscribers. Sufficient for a single-instance monolith. */
@Component
public class TaskProgressHub implements TaskEventPublisher, SubscribeTaskProgressUseCase {

    private final Map<UUID, Set<Consumer<TaskProgressEvent>>> listeners = new ConcurrentHashMap<>();

    @Override
    public void publish(TaskProgressEvent event) {
        listeners.getOrDefault(event.taskId(), Set.of()).forEach(listener -> listener.accept(event));
    }

    @Override
    public AutoCloseable subscribe(UUID taskId, Consumer<TaskProgressEvent> listener) {
        listeners.computeIfAbsent(taskId, id -> ConcurrentHashMap.newKeySet()).add(listener);
        return () -> listeners.computeIfPresent(taskId, (id, set) -> {
            set.remove(listener);
            return set.isEmpty() ? null : set;
        });
    }
}
