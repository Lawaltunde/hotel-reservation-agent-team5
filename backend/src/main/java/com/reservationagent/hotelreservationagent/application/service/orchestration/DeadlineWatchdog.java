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
import com.reservationagent.hotelreservationagent.application.service.task.TaskSubmittedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
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
    private final Duration strandedAfter;

    private static final Duration STUCK_CALL_GRACE = Duration.ofSeconds(90);
    private static final Set<TaskStatus> STRANDABLE =
            EnumSet.of(TaskStatus.VALIDATING, TaskStatus.DISCOVERING, TaskStatus.RANKING);
    private static final Logger log = LoggerFactory.getLogger(DeadlineWatchdog.class);
    private static final Set<CallStatus> ACTIVE =
            EnumSet.of(CallStatus.QUEUED, CallStatus.INITIATED, CallStatus.RINGING, CallStatus.IN_PROGRESS);

    public DeadlineWatchdog(BookingTaskRepository tasks, TaskOrchestrator orchestrator, TaskStateService state,
                            AgentPolicy policy, Clock clock, PhoneCallRepository calls,
                            @Value("${hotel-agent.policy.stranded-after:PT2M}") Duration strandedAfter) {
        this.calls = calls;
        this.strandedAfter = strandedAfter;
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

    /**
     * Work before the calls runs in memory (@Async), so a restart strands a task in VALIDATING,
     * DISCOVERING or RANKING. Those steps take seconds; one unchanged for {@code strandedAfter} is stranded.
     */
    private boolean isStranded(BookingTask task, Instant now) {
        return STRANDABLE.contains(task.status()) && task.updatedAt().isBefore(now.minus(strandedAfter));
    }

    private void strandedRecovery(BookingTask task) {
        if (task.status() == TaskStatus.VALIDATING && !task.isPastDeadline(clock.instant())) {
            log.warn("task={} stranded in VALIDATING (restart?): starting it again", task.id());
            orchestrator.onSubmitted(new TaskSubmittedEvent(task.id()));
        } else if (task.status() == TaskStatus.VALIDATING) {
            state.transition(task.id(), TaskStatus.DEADLINE_EXCEEDED, "Deadline exceeded before the search started");
        } else {
            log.warn("task={} stranded in {} (restart?): sending the report", task.id(), task.status());
            orchestrator.rescue(task.id());
        }
    }

    @Scheduled(fixedDelayString = "${hotel-agent.policy.watchdog-interval:5s}")
    public void check() {
        Instant now = clock.instant();
        for (BookingTask task : tasks.findActive()) {
            if (task.status() == TaskStatus.CALLING_FOR_QUOTES && expireStuckCalls(task, now)) {
                orchestrator.onCallFinished(task.id());
            }
            if (isStranded(task, now)) {
                strandedRecovery(task);
            } else if (task.isPastDeadline(now)) {
                if (task.status() == TaskStatus.CALLING_FOR_QUOTES) {
                    // Missed the finalization window (e.g. the app was down): report the best so far.
                    orchestrator.selectOffer(task.id());
                } else {
                    state.transition(task.id(), TaskStatus.DEADLINE_EXCEEDED, "Deadline exceeded");
                }
            } else if (task.status() == TaskStatus.CALLING_FOR_QUOTES
                    && now.isAfter(task.deadline().minus(policy.deadlineReserve()))) {
                orchestrator.selectOffer(task.id());
            }
        }
    }
}
