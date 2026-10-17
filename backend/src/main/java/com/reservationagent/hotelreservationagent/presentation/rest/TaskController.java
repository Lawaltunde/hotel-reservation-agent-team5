package com.reservationagent.hotelreservationagent.presentation.rest;

import com.reservationagent.hotelreservationagent.application.event.TaskProgressEvent;
import com.reservationagent.hotelreservationagent.application.port.in.GetBookingTaskUseCase;
import com.reservationagent.hotelreservationagent.application.port.in.GetBookingTaskUseCase.TaskSnapshot;
import com.reservationagent.hotelreservationagent.application.port.in.SubmitBookingTaskUseCase;
import com.reservationagent.hotelreservationagent.application.port.in.SubscribeTaskProgressUseCase;
import com.reservationagent.hotelreservationagent.domain.task.BookingTask;
import com.reservationagent.hotelreservationagent.presentation.dto.CreateTaskRequest;
import com.reservationagent.hotelreservationagent.presentation.dto.ProgressEventResponse;
import com.reservationagent.hotelreservationagent.presentation.dto.TaskAcceptedResponse;
import com.reservationagent.hotelreservationagent.presentation.dto.TaskResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private static final Logger log = LoggerFactory.getLogger(TaskController.class);
    static final String PROGRESS_EVENT = "progress";
    /** Upper bound for an SSE stream; tasks normally finish well within their deadline. */
    private static final Duration MAX_STREAM = Duration.ofHours(2);

    private final SubmitBookingTaskUseCase submitTask;
    private final GetBookingTaskUseCase getTask;
    private final SubscribeTaskProgressUseCase progress;

    public TaskController(SubmitBookingTaskUseCase submitTask, GetBookingTaskUseCase getTask,
                          SubscribeTaskProgressUseCase progress) {
        this.submitTask = submitTask;
        this.getTask = getTask;
        this.progress = progress;
    }

    /** Starts a task. Idempotent on {@code requestId}: a repeat returns the existing task. */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TaskAcceptedResponse> create(@Valid @RequestBody CreateTaskRequest request) {
        UUID taskId = submitTask.submit(new SubmitBookingTaskUseCase.Command(
                request.requestId().trim(),
                request.city(),
                request.checkIn(),
                request.checkOut(),
                request.rooms(),
                request.adults(),
                request.children() == null ? 0 : request.children(),
                request.destinationEmail().trim(),
                request.deadline(),
                request.requiredAmenities() == null ? null : new LinkedHashSet<>(request.requiredAmenities()),
                request.authorizationMode(),
                request.maximumTotalCad(),
                Boolean.TRUE.equals(request.allowCompetingQuoteDisclosure())));
        BookingTask task = snapshot(taskId).task();
        return ResponseEntity.accepted()
                .location(URI.create("/api/tasks/" + taskId))
                .body(new TaskAcceptedResponse(taskId, task.status()));
    }

    @GetMapping("/{id}")
    public TaskResponse get(@PathVariable UUID id) {
        return TaskResponse.from(snapshot(id));
    }

    /**
     * Server-sent events: one {@code progress} event with the current status on connect, then one
     * per status change. The stream completes once the task reaches a terminal status.
     */
    @GetMapping(path = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable UUID id) {
        BookingTask initial = snapshot(id).task();
        SseEmitter emitter = new SseEmitter(MAX_STREAM.toMillis());
        AtomicReference<AutoCloseable> subscription = new AtomicReference<>();
        Runnable unsubscribe = () -> {
            AutoCloseable handle = subscription.getAndSet(null);
            if (handle != null) {
                try {
                    handle.close();
                } catch (Exception e) {
                    log.debug("task={} unsubscribe failed", id, e);
                }
            }
        };
        emitter.onCompletion(unsubscribe);
        emitter.onTimeout(unsubscribe);
        emitter.onError(e -> unsubscribe.run());

        // Subscribe before reading the current status so no transition is missed in between. Live
        // events that arrive before the initial event is sent are held back, then only those newer
        // than the snapshot are forwarded, so the client never sees a status go backwards.
        Object lock = new Object();
        List<TaskProgressEvent> pending = new ArrayList<>();
        AtomicReference<BookingTask> sentSnapshot = new AtomicReference<>();
        subscription.set(progress.subscribe(id, event -> {
            synchronized (lock) {
                BookingTask snapshot = sentSnapshot.get();
                if (snapshot == null) {
                    pending.add(event);
                } else if (isNewer(event, snapshot)) {
                    send(emitter, event, unsubscribe);
                }
            }
        }));
        synchronized (lock) {
            BookingTask current = getTask.get(id).map(TaskSnapshot::task).orElse(initial);
            sentSnapshot.set(current);
            send(emitter, new TaskProgressEvent(id, current.status(), "Current status", current.updatedAt()), unsubscribe);
            pending.stream().filter(e -> isNewer(e, current)).forEach(e -> send(emitter, e, unsubscribe));
            pending.clear();
        }
        return emitter;
    }

    /** Events are stamped with the task's persisted updatedAt, so older or equal ones are already reflected. */
    private static boolean isNewer(TaskProgressEvent event, BookingTask snapshot) {
        return event.status() != snapshot.status() && !event.at().isBefore(snapshot.updatedAt());
    }

    private static void send(SseEmitter emitter, TaskProgressEvent event, Runnable unsubscribe) {
        try {
            emitter.send(SseEmitter.event()
                    .name(PROGRESS_EVENT)
                    .data(ProgressEventResponse.from(event), MediaType.APPLICATION_JSON));
            if (event.status().isTerminal()) {
                unsubscribe.run();
                emitter.complete();
            }
        } catch (IOException | IllegalStateException e) {
            // Client went away or the emitter is already complete.
            unsubscribe.run();
        }
    }

    private TaskSnapshot snapshot(UUID id) {
        return getTask.get(id).orElseThrow(() -> new TaskNotFoundException(id));
    }
}
