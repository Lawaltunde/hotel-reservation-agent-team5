package com.reservationagent.hotelreservationagent.unit.application;

import com.reservationagent.hotelreservationagent.application.policy.AgentPolicy;
import com.reservationagent.hotelreservationagent.application.port.out.BookingTaskRepository;
import com.reservationagent.hotelreservationagent.application.port.out.PhoneCallRepository;
import com.reservationagent.hotelreservationagent.application.service.orchestration.DeadlineWatchdog;
import com.reservationagent.hotelreservationagent.application.service.orchestration.TaskOrchestrator;
import com.reservationagent.hotelreservationagent.application.service.orchestration.TaskStateService;
import com.reservationagent.hotelreservationagent.application.service.task.TaskSubmittedEvent;
import com.reservationagent.hotelreservationagent.domain.hotel.PropertyType;
import com.reservationagent.hotelreservationagent.domain.task.BookingTask;
import com.reservationagent.hotelreservationagent.domain.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static com.reservationagent.hotelreservationagent.support.Fixtures.CLOCK;
import static com.reservationagent.hotelreservationagent.support.Fixtures.NOW;
import static com.reservationagent.hotelreservationagent.support.Fixtures.task;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A restart strands in-memory work; the watchdog still gets every task to an email. */
class DeadlineWatchdogTest {

    private final BookingTaskRepository tasks = mock(BookingTaskRepository.class);
    private final TaskOrchestrator orchestrator = mock(TaskOrchestrator.class);
    private final TaskStateService state = mock(TaskStateService.class);
    private final PhoneCallRepository calls = mock(PhoneCallRepository.class);
    private final AgentPolicy policy = new AgentPolicy(15, 60, 6, Duration.ofSeconds(2), 3, 8, 2,
            Duration.ofSeconds(240), Duration.ofSeconds(45), Set.of(PropertyType.HOTEL));
    private final DeadlineWatchdog watchdog = new DeadlineWatchdog(tasks, orchestrator, state, policy, CLOCK, calls,
            Duration.ofMinutes(2));

    private static BookingTask at(TaskStatus status, Instant updatedAt, Instant deadline) {
        BookingTask t = task();
        return new BookingTask(t.id(), t.requestId(), t.municipalityId(), t.stay(), t.destinationEmail(),
                t.authorizationMode(), t.maximumTotal(), t.requiredAmenities(), t.allowCompetingQuoteDisclosure(),
                t.intake(), deadline, status, null, t.createdAt(), updatedAt);
    }

    @Test
    void aTaskStrandedInDiscoveryIsReported() {
        BookingTask stranded = at(TaskStatus.DISCOVERING, NOW.minusSeconds(300), NOW.plusSeconds(300));
        when(tasks.findActive()).thenReturn(List.of(stranded));

        watchdog.check();

        verify(orchestrator).rescue(stranded.id());
    }

    @Test
    void aTaskStrandedInValidationIsStartedAgain() {
        BookingTask stranded = at(TaskStatus.VALIDATING, NOW.minusSeconds(300), NOW.plusSeconds(300));
        when(tasks.findActive()).thenReturn(List.of(stranded));

        watchdog.check();

        verify(orchestrator).onSubmitted(new TaskSubmittedEvent(stranded.id()));
    }

    @Test
    void aDiscoveryInProgressIsLeftAlone() {
        BookingTask running = at(TaskStatus.DISCOVERING, NOW.minusSeconds(10), NOW.plusSeconds(300));
        when(tasks.findActive()).thenReturn(List.of(running));

        watchdog.check();

        verify(orchestrator, never()).rescue(any());
        verify(state, never()).transition(any(), any(), any());
    }

    @Test
    void callsPastTheDeadlineStillReportTheBestSoFar() {
        BookingTask late = at(TaskStatus.CALLING_FOR_QUOTES, NOW.minusSeconds(600), NOW.minusSeconds(5));
        when(tasks.findActive()).thenReturn(List.of(late));

        watchdog.check();

        verify(orchestrator).selectOffer(late.id());
        verify(state, never()).transition(eq(late.id()), eq(TaskStatus.DEADLINE_EXCEEDED), any());
    }
}
