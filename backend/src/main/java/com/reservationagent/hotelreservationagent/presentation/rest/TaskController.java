package com.reservationagent.hotelreservationagent.presentation.rest;

import com.reservationagent.hotelreservationagent.application.event.TaskProgressEvent;
import com.reservationagent.hotelreservationagent.application.port.in.GetBookingTaskUseCase;
import com.reservationagent.hotelreservationagent.application.port.in.GetBookingTaskUseCase.TaskSnapshot;
import com.reservationagent.hotelreservationagent.application.port.in.SubmitBookingTaskUseCase;
import com.reservationagent.hotelreservationagent.application.port.in.SubmitTaskPromptUseCase;
import com.reservationagent.hotelreservationagent.application.port.in.SubscribeTaskProgressUseCase;
import com.reservationagent.hotelreservationagent.domain.task.BookingTask;
import com.reservationagent.hotelreservationagent.domain.task.TaskIntake;
import com.reservationagent.hotelreservationagent.presentation.dto.CreateTaskRequest;
import com.reservationagent.hotelreservationagent.presentation.dto.ProgressEventResponse;
import com.reservationagent.hotelreservationagent.presentation.dto.PromptTaskRequest;
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
import com.reservationagent.hotelreservationagent.domain.task.TaskStatus;
import org.springframework.beans.factory.annotation.Value;
import java.util.function.Consumer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
    private final SubmitTaskPromptUseCase submitPrompt;
    private final GetBookingTaskUseCase getTask;
    private final SubscribeTaskProgressUseCase progress;
    private final Duration heartbeatInterval;
    /** One daemon thread sends every stream's heartbeat; each tick is a single read and a tiny write. */
    private static final ScheduledExecutorService HEARTBEATS = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sse-heartbeat");
        t.setDaemon(true);
        return t;
    });

    public TaskController(SubmitBookingTaskUseCase submitTask, SubmitTaskPromptUseCase submitPrompt,
                          GetBookingTaskUseCase getTask, SubscribeTaskProgressUseCase progress,
                          @Value("${app.sse.heartbeat:15s}") Duration heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
        this.submitTask = submitTask;
        this.submitPrompt = submitPrompt;
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
                Boolean.TRUE.equals(request.allowCompetingQuoteDisclosure()),
                TaskIntake.FORM));
        return accepted(taskId);
    }

    /**
     * Starts a task from one free-text prompt. The same validation applies as for {@link #create};
     * 400 errors name the {@code request} fields (city, checkIn, checkOut, email, ...), and 503 means
     * the AI provider could not read the prompt. Idempotent on the optional {@code Idempotency-Key}.
     */
    @PostMapping(path = "/prompt", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TaskAcceptedResponse> createFromPrompt(
            @Valid @RequestBody PromptTaskRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        return accepted(submitPrompt.submit(request.prompt().strip(), idempotencyKey));
    }

    private ResponseEntity<TaskAcceptedResponse> accepted(UUID taskId) {
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
        AtomicReference<ScheduledFuture<?>> heartbeat = new AtomicReference<>();
        Runnable unsubscribe = () -> {
            ScheduledFuture<?> beat = heartbeat.getAndSet(null);
            if (beat != null) {
                beat.cancel(false);
            }
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
        // events that arrive before the initial event is sent are held back; afterwards only events
        // newer than the last one sent are forwarded, so the client never sees a status go backwards.
        Object lock = new Object();
        List<TaskProgressEvent> pending = new ArrayList<>();
        AtomicReference<TaskProgressEvent> lastSent = new AtomicReference<>();
        Consumer<TaskProgressEvent> forward = event -> {
            if (isNewer(event, lastSent.get())) {
                lastSent.set(event);
                send(emitter, event, unsubscribe);
            }
        };
        subscription.set(progress.subscribe(id, event -> {
            synchronized (lock) {
                if (lastSent.get() == null) {
                    pending.add(event);
                } else {
                    forward.accept(event);
                }
            }
        }));
        synchronized (lock) {
            BookingTask current = getTask.get(id).map(TaskSnapshot::task).orElse(initial);
            TaskProgressEvent first = new TaskProgressEvent(id, current.status(), describe(current.status()),
                    current.updatedAt());
            lastSent.set(first);
            send(emitter, first, unsubscribe);
            pending.forEach(forward);
            pending.clear();
        }
        // Safety net and keep-alive: every heartbeat, re-read the task. A transition whose live event
        // was missed is sent (and a terminal one completes the stream); otherwise a comment keeps
        // proxies from closing an idle stream and detects clients that went away.
        if (!lastSent.get().status().isTerminal()) {
            heartbeat.set(HEARTBEATS.scheduleAtFixedRate(() -> {
                synchronized (lock) {
                    try {
                        BookingTask now = getTask.get(id).map(TaskSnapshot::task).orElse(null);
                        if (now != null && now.status() != lastSent.get().status()) {
                            forward.accept(new TaskProgressEvent(id, now.status(), describe(now.status()), now.updatedAt()));
                        } else {
                            emitter.send(SseEmitter.event().comment("keep-alive"));
                        }
                    } catch (IOException | IllegalStateException e) {
                        unsubscribe.run();
                    } catch (RuntimeException e) {
                        log.debug("task={} heartbeat failed: {}", id, e.getMessage());
                    }
                }
            }, heartbeatInterval.toMillis(), heartbeatInterval.toMillis(), TimeUnit.MILLISECONDS));
        }
        return emitter;
    }

    /** Newer = a different status, not older than the last event sent (events carry the persisted updatedAt). */
    private static boolean isNewer(TaskProgressEvent event, TaskProgressEvent last) {
        return last == null || (event.status() != last.status() && !event.at().isBefore(last.at()));
    }

    /** What a status means, for the event sent on connect and for transitions found by the heartbeat. */
    static String describe(TaskStatus status) {
        return switch (status) {
            case RECEIVED -> "Request received";
            case VALIDATING -> "Checking the request";
            case DISCOVERING -> "Searching for hotels";
            case RANKING -> "Ranking hotels to call";
            case CALLING_FOR_QUOTES -> "Calling hotels for availability and prices";
            case OFFER_SELECTED -> "Cheapest confirmed option selected";
            case BOOKING, VERIFYING -> "Booking in progress";
            case EMAIL_PENDING -> "Sending the report email";
            case COMPLETED -> "Report email sent";
            case INVALID_REQUEST -> "The request could not be used";
            case OUT_OF_SCOPE_LOCATION -> "The location is outside Ontario";
            case NO_CANDIDATES -> "No callable hotels found";
            case NO_VALID_OFFER -> "No confirmed available offer";
            case BOOKING_UNCERTAIN -> "Booking status uncertain";
            case BOOKING_FAILED -> "Booking failed";
            case EMAIL_FAILED -> "The report email could not be sent";
            case DEADLINE_EXCEEDED -> "Deadline exceeded";
        };
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
