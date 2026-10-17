package com.reservationagent.hotelreservationagent.application.service.call;

import com.reservationagent.hotelreservationagent.application.policy.AgentPolicy;
import com.reservationagent.hotelreservationagent.application.port.out.HotelIndex;
import com.reservationagent.hotelreservationagent.application.port.out.PhoneCallRepository;
import com.reservationagent.hotelreservationagent.application.port.out.VoiceCallProvider;
import com.reservationagent.hotelreservationagent.domain.call.CallStatus;
import com.reservationagent.hotelreservationagent.domain.call.PhoneCall;
import com.reservationagent.hotelreservationagent.domain.candidate.HotelCandidate;
import com.reservationagent.hotelreservationagent.domain.hotel.Hotel;
import com.reservationagent.hotelreservationagent.domain.task.BookingTask;
import com.reservationagent.hotelreservationagent.domain.task.TaskIntake;
import com.reservationagent.hotelreservationagent.domain.task.TaskIntake.StatedField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Starts quote calls in small batches. Never exceeds {@code maxParallelCalls} active calls per task,
 * and never starts a call that cannot finish before the protected finalization window.
 */
@Service
public class CallScheduler {

    private static final Logger log = LoggerFactory.getLogger(CallScheduler.class);
    /** What the voice agent sees for a value the guest did not give; it must not invent one. */
    static final String NOT_SPECIFIED = "not specified";

    private final VoiceCallProvider voice;
    private final PhoneCallRepository calls;
    private final HotelIndex hotels;
    private final AgentPolicy policy;
    private final Clock clock;

    public CallScheduler(VoiceCallProvider voice, PhoneCallRepository calls, HotelIndex hotels, AgentPolicy policy, Clock clock) {
        this.voice = voice;
        this.calls = calls;
        this.hotels = hotels;
        this.policy = policy;
        this.clock = clock;
    }

    /** @return number of calls started */
    public synchronized int startNextBatch(BookingTask task, List<HotelCandidate> rankedCandidates) {
        if (!hasTimeForAnotherCall(task)) {
            return 0;
        }
        List<PhoneCall> existing = calls.findByTask(task.id());
        Set<UUID> alreadyCalled = existing.stream().map(PhoneCall::hotelId).collect(Collectors.toSet());
        int slots = Math.min(policy.maxParallelCalls() - calls.countActive(task.id()),
                policy.maxTotalCalls() - existing.size());

        int started = 0;
        for (HotelCandidate candidate : rankedCandidates) {
            if (started >= slots) break;
            if (alreadyCalled.contains(candidate.hotelId())) continue;
            Hotel hotel = hotels.findById(candidate.hotelId()).orElse(null);
            if (hotel == null || hotel.bestPhone() == null) continue;
            start(task, hotel);
            started++;
        }
        return started;
    }

    public boolean hasTimeForAnotherCall(BookingTask task) {
        Instant latestStart = task.deadline().minus(policy.deadlineReserve()).minus(policy.maxCallDuration());
        return clock.instant().isBefore(latestStart);
    }

    private void start(BookingTask task, Hotel hotel) {
        PhoneCall call = calls.save(new PhoneCall(UUID.randomUUID(), task.id(), hotel.id(), hotel.bestPhone(),
                null, null, CallStatus.QUEUED, null, clock.instant(), null, null));
        voice.startCall(hotel.bestPhone(), dynamicVariables(task, hotel))
                .whenComplete((started, error) -> {
                    if (error != null) {
                        log.warn("task={} hotel={} call start failed: {}", task.id(), hotel.id(), error.getMessage());
                        calls.save(withStatus(call, CallStatus.FAILED, null, null, error.getMessage()));
                    } else {
                        calls.save(withStatus(call, CallStatus.INITIATED, started.providerCallId(), started.conversationId(), null));
                    }
                });
    }

    /** Variables substituted into the fixed ElevenLabs agent prompt (docs/voice-agent-prompt.md). */
    private Map<String, String> dynamicVariables(BookingTask task, Hotel hotel) {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("task_id", task.id().toString());
        vars.put("hotel_name", hotel.name());
        vars.put("check_in", task.stay().checkIn().toString());
        vars.put("check_out", task.stay().checkOut().toString());
        vars.put("nights", String.valueOf(task.stay().nights()));
        TaskIntake intake = task.intake();
        vars.put("rooms", stated(intake, StatedField.ROOMS, task.stay().rooms()));
        vars.put("adults", stated(intake, StatedField.ADULTS, task.stay().adults()));
        vars.put("children", stated(intake, StatedField.CHILDREN, task.stay().children()));
        vars.put("stay_request", stayRequest(task));
        vars.put("max_negotiation_rounds", String.valueOf(policy.maxNegotiationRounds()));
        vars.put("authorization_mode", task.authorizationMode().name());
        vars.put("required_amenities", String.join(", ", task.requiredAmenities()));
        return vars;
    }

    private static String stated(TaskIntake intake, StatedField field, int value) {
        return intake.isStated(field) ? String.valueOf(value) : NOT_SPECIFIED;
    }

    /**
     * One sentence the agent can say, using only what the guest stated. Unstated counts are left out
     * so the hotel quotes its cheapest available room at standard occupancy.
     */
    static String stayRequest(BookingTask task) {
        TaskIntake in = task.intake();
        StringBuilder s = new StringBuilder();
        s.append(in.isStated(StatedField.ROOMS) && task.stay().rooms() > 1 ? task.stay().rooms() + " rooms" : "a room");
        if (in.isStated(StatedField.ADULTS)) {
            s.append(" for ").append(task.stay().adults()).append(task.stay().adults() == 1 ? " adult" : " adults");
            if (in.isStated(StatedField.CHILDREN) && task.stay().children() > 0) {
                s.append(" and ").append(task.stay().children()).append(task.stay().children() == 1 ? " child" : " children");
            }
        }
        long nights = task.stay().nights();
        s.append(", checking in ").append(task.stay().checkIn()).append(" and checking out ")
                .append(task.stay().checkOut()).append(" (").append(nights).append(nights == 1 ? " night)" : " nights)");
        return s.toString();
    }

    private static PhoneCall withStatus(PhoneCall c, CallStatus status, String providerId, String conversationId, String failure) {
        return new PhoneCall(c.id(), c.taskId(), c.hotelId(), c.dialledNumber(), providerId, conversationId, status,
                c.outcome(), c.startedAt(), status == CallStatus.FAILED ? Instant.now() : null, failure);
    }
}
