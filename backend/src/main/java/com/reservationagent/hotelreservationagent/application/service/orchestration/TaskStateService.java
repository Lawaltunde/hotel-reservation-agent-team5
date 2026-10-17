package com.reservationagent.hotelreservationagent.application.service.orchestration;

import com.reservationagent.hotelreservationagent.application.event.TaskProgressEvent;
import com.reservationagent.hotelreservationagent.application.port.out.BookingTaskRepository;
import com.reservationagent.hotelreservationagent.application.port.out.TaskEventPublisher;
import com.reservationagent.hotelreservationagent.domain.task.BookingTask;
import com.reservationagent.hotelreservationagent.domain.task.TaskOutcome;
import com.reservationagent.hotelreservationagent.domain.task.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.util.UUID;

/** The only place task status changes; every change is persisted and pushed to the UI. */
@Component
public class TaskStateService {

    private static final Logger log = LoggerFactory.getLogger(TaskStateService.class);

    private final BookingTaskRepository tasks;
    private final TaskEventPublisher publisher;
    private final Clock clock;

    public TaskStateService(BookingTaskRepository tasks, TaskEventPublisher publisher, Clock clock) {
        this.tasks = tasks;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Transactional
    public BookingTask transition(UUID taskId, TaskStatus target, String message) {
        BookingTask task = tasks.findById(taskId).orElseThrow();
        if (task.status() == target) {
            return task;
        }
        BookingTask updated = tasks.save(task.transitionTo(target, clock.instant()));
        log.info("task={} {} -> {}: {}", taskId, task.status(), target, message);
        // Stamped with the persisted updatedAt so subscribers can order it against a snapshot.
        publishAfterCommit(new TaskProgressEvent(taskId, target, message, updated.updatedAt()));
        return updated;
    }

    /** Subscribers that re-read the task must see the new status, so publish only once it is committed. */
    private void publishAfterCommit(TaskProgressEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publisher.publish(event);
                }
            });
        } else {
            publisher.publish(event);
        }
    }

    @Transactional
    public BookingTask recordOutcome(UUID taskId, TaskOutcome outcome) {
        BookingTask task = tasks.findById(taskId).orElseThrow();
        return tasks.save(task.withOutcome(outcome, clock.instant()));
    }

    public void announce(UUID taskId, TaskStatus status, String message) {
        publisher.publish(new TaskProgressEvent(taskId, status, message, clock.instant()));
    }
}
