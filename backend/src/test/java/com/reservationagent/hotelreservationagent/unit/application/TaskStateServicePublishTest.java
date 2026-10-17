package com.reservationagent.hotelreservationagent.unit.application;

import com.reservationagent.hotelreservationagent.application.event.TaskProgressEvent;
import com.reservationagent.hotelreservationagent.application.port.out.BookingTaskRepository;
import com.reservationagent.hotelreservationagent.application.service.orchestration.TaskStateService;
import com.reservationagent.hotelreservationagent.application.service.progress.TaskProgressHub;
import com.reservationagent.hotelreservationagent.domain.task.BookingTask;
import com.reservationagent.hotelreservationagent.domain.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.reservationagent.hotelreservationagent.support.Fixtures.CLOCK;
import static com.reservationagent.hotelreservationagent.support.Fixtures.task;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Transitions made off the request thread (scheduler, watchdog) reach live subscribers too. */
class TaskStateServicePublishTest {

    @Test
    void aTransitionOnASchedulerThreadWithoutATransactionIsPublished() throws Exception {
        BookingTaskRepository tasks = mock(BookingTaskRepository.class);
        BookingTask emailPending = task().transitionTo(TaskStatus.NO_VALID_OFFER, CLOCK.instant())
                .transitionTo(TaskStatus.EMAIL_PENDING, CLOCK.instant());
        when(tasks.findById(emailPending.id())).thenReturn(Optional.of(emailPending));
        when(tasks.save(any())).thenAnswer(inv -> inv.getArgument(0));
        TaskProgressHub hub = new TaskProgressHub();
        TaskStateService state = new TaskStateService(tasks, hub, CLOCK);
        List<TaskProgressEvent> received = new CopyOnWriteArrayList<>();
        hub.subscribe(emailPending.id(), received::add);

        Thread scheduler = Thread.ofPlatform().name("scheduling-1").start(() ->
                state.transition(emailPending.id(), TaskStatus.COMPLETED, "Report email accepted by provider"));
        scheduler.join();

        assertThat(received).singleElement().satisfies(e -> {
            assertThat(e.status()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(e.status().isTerminal()).isTrue();
        });
    }
}
