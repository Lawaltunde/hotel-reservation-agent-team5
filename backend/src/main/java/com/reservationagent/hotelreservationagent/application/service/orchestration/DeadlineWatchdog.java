package com.reservationagent.hotelreservationagent.application.service.orchestration;

import com.reservationagent.hotelreservationagent.application.policy.AgentPolicy;
import com.reservationagent.hotelreservationagent.application.port.out.BookingTaskRepository;
import com.reservationagent.hotelreservationagent.application.port.out.PhoneCallRepository;
import com.reservationagent.hotelreservationagent.domain.call.CallOutcome;
import com.reservationagent.hotelreservationagent.domain.call.CallStatus;
import com.reservationagent.hotelreservationagent.domain.call.PhoneCall;
import com.reservationagent.hotelreservationagent.domain.task.BookingTask;
import com.reservationagent.hotelreservationagent.domain.task.TaskStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/** Protects the finalization window: stops calling in time to email the user before the deadline. */
@Component
public class DeadlineWatchdog {

    private final BookingTaskRepository tasks;
    private final TaskOrchestrator orchestrator;
    private final TaskStateService state;
    private final AgentPolicy policy;
    private final Clock clock;
    private final PhoneCallRepository calls;

    private static final Duration STUCK_CALL_GRACE = Duration.ofSeconds(90);
    private static final Set<CallStatus> ACTIVE =
            EnumSet.of(CallStatus.QUEUED, CallStatus.INITIATED, CallStatus.RINGING, CallStatus.IN_PROGRESS);

    public DeadlineWatchdog(BookingTaskRepository tasks, TaskOrchestrator orchestrator, TaskStateService state,
                            AgentPolicy policy, Clock clock, PhoneCallRepository calls) {
        this.calls = calls;
        this.tasks = tasks;
        this.orchestrator = orchestrator;
        this.state = state;
        this.policy = policy;
        this.clock = clock;
    }

    /**
     * A call with no post-call webhook long after it should have ended (no answer, provider error)
     * would block the task until the deadline. Marks such calls FAILED.
     *
     * @return true if any call was expired
     */
    private boolean expireStuckCalls(BookingTask task, Instant now) {
        Instant cutoff = now.minus(policy.maxCallDuration()).minus(STUCK_CALL_GRACE);
        boolean expired = false;
        for (PhoneCall c : calls.findByTask(task.id())) {
            if (ACTIVE.contains(c.status()) && c.startedAt() != null && c.startedAt().isBefore(cutoff)) {
                calls.save(new PhoneCall(c.id(), c.taskId(), c.hotelId(), c.dialledNumber(), c.providerCallId(),
                        c.conversationId(), CallStatus.FAILED, c.outcome() == null ? CallOutcome.UNKNOWN : c.outcome(),
                        c.startedAt(), now, "No post-call result within the call time limit"));
                expired = true;
            }
        }
        return expired;
    }

    @Scheduled(fixedDelayString = "${hotel-agent.policy.watchdog-interval:5s}")
    public void check() {
        Instant now = clock.instant();
        for (BookingTask task : tasks.findActive()) {
            if (task.status() == TaskStatus.CALLING_FOR_QUOTES && expireStuckCalls(task, now)) {
                orchestrator.onCallFinished(task.id());
            }
            if (task.isPastDeadline(now)) {
                state.transition(task.id(), TaskStatus.DEADLINE_EXCEEDED, "Deadline exceeded");
            } else if (task.status() == TaskStatus.CALLING_FOR_QUOTES
                    && now.isAfter(task.deadline().minus(policy.deadlineReserve()))) {
                orchestrator.selectOffer(task.id());
            }
        }
    }
}
