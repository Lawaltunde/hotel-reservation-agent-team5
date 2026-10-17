package com.reservationagent.hotelreservationagent.application.service.orchestration;

import com.reservationagent.hotelreservationagent.application.policy.AgentPolicy;
import com.reservationagent.hotelreservationagent.application.port.out.BookingTaskRepository;
import com.reservationagent.hotelreservationagent.domain.task.BookingTask;
import com.reservationagent.hotelreservationagent.domain.task.TaskStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/** Protects the finalization window: stops calling in time to email the user before the deadline. */
@Component
public class DeadlineWatchdog {

    private final BookingTaskRepository tasks;
    private final TaskOrchestrator orchestrator;
    private final TaskStateService state;
    private final AgentPolicy policy;
    private final Clock clock;

    public DeadlineWatchdog(BookingTaskRepository tasks, TaskOrchestrator orchestrator, TaskStateService state,
                            AgentPolicy policy, Clock clock) {
        this.tasks = tasks;
        this.orchestrator = orchestrator;
        this.state = state;
        this.policy = policy;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${hotel-agent.policy.watchdog-interval:5s}")
    public void check() {
        Instant now = clock.instant();
        for (BookingTask task : tasks.findActive()) {
            if (task.isPastDeadline(now)) {
                state.transition(task.id(), TaskStatus.DEADLINE_EXCEEDED, "Deadline exceeded");
            } else if (task.status() == TaskStatus.CALLING_FOR_QUOTES
                    && now.isAfter(task.deadline().minus(policy.deadlineReserve()))) {
                orchestrator.selectOffer(task.id());
            }
        }
    }
}
