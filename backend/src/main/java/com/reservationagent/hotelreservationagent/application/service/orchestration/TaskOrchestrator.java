package com.reservationagent.hotelreservationagent.application.service.orchestration;

import com.reservationagent.hotelreservationagent.application.port.out.BookingTaskRepository;
import com.reservationagent.hotelreservationagent.application.port.out.HotelCandidateRepository;
import com.reservationagent.hotelreservationagent.application.port.out.HotelIndex;
import com.reservationagent.hotelreservationagent.application.port.out.MunicipalityRepository;
import com.reservationagent.hotelreservationagent.application.port.out.OfferRepository;
import com.reservationagent.hotelreservationagent.application.port.out.PhoneCallRepository;
import com.reservationagent.hotelreservationagent.application.port.out.ReservationRepository;
import com.reservationagent.hotelreservationagent.application.port.out.TranscriptRepository;
import com.reservationagent.hotelreservationagent.application.service.booking.BookingCoordinator;
import com.reservationagent.hotelreservationagent.application.service.call.CallScheduler;
import com.reservationagent.hotelreservationagent.application.service.discovery.CandidateDiscoveryService;
import com.reservationagent.hotelreservationagent.application.service.notification.NotificationService;
import com.reservationagent.hotelreservationagent.application.service.notification.ReportComposer.ReportInput;
import com.reservationagent.hotelreservationagent.application.service.pricing.OfferEvaluator;
import com.reservationagent.hotelreservationagent.application.service.task.TaskSubmittedEvent;
import com.reservationagent.hotelreservationagent.domain.candidate.HotelCandidate;
import com.reservationagent.hotelreservationagent.domain.geography.Municipality;
import com.reservationagent.hotelreservationagent.domain.offer.HotelOffer;
import com.reservationagent.hotelreservationagent.domain.task.BookingTask;
import com.reservationagent.hotelreservationagent.domain.task.TaskOutcome;
import com.reservationagent.hotelreservationagent.domain.task.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Drives a task through the state machine (README §6). Calls are asynchronous: this class starts a
 * batch, then resumes in {@link #onCallFinished(UUID)} when the post-call webhook arrives.
 */
@Service
public class TaskOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(TaskOrchestrator.class);

    private final BookingTaskRepository tasks;
    private final MunicipalityRepository municipalities;
    private final HotelCandidateRepository candidates;
    private final HotelIndex hotelIndex;
    private final PhoneCallRepository calls;
    private final OfferRepository offers;
    private final TranscriptRepository transcripts;
    private final ReservationRepository reservations;
    private final CandidateDiscoveryService discovery;
    private final CallScheduler scheduler;
    private final OfferEvaluator evaluator;
    private final BookingCoordinator bookingCoordinator;
    private final NotificationService notifications;
    private final TaskStateService state;

    public TaskOrchestrator(BookingTaskRepository tasks, MunicipalityRepository municipalities,
                            HotelCandidateRepository candidates, HotelIndex hotelIndex, PhoneCallRepository calls,
                            OfferRepository offers, TranscriptRepository transcripts, ReservationRepository reservations,
                            CandidateDiscoveryService discovery, CallScheduler scheduler, OfferEvaluator evaluator,
                            BookingCoordinator bookingCoordinator, NotificationService notifications,
                            TaskStateService state) {
        this.tasks = tasks;
        this.municipalities = municipalities;
        this.candidates = candidates;
        this.hotelIndex = hotelIndex;
        this.calls = calls;
        this.offers = offers;
        this.transcripts = transcripts;
        this.reservations = reservations;
        this.discovery = discovery;
        this.scheduler = scheduler;
        this.evaluator = evaluator;
        this.bookingCoordinator = bookingCoordinator;
        this.notifications = notifications;
        this.state = state;
    }

    @Async
    @TransactionalEventListener
    public void onSubmitted(TaskSubmittedEvent event) {
        UUID taskId = event.taskId();
        try {
            BookingTask task = state.transition(taskId, TaskStatus.DISCOVERING, "Searching the Ontario hotel index");
            Municipality municipality = municipalities.findById(task.municipalityId()).orElseThrow();
            List<HotelCandidate> ranked = discovery.discover(task, municipality);
            if (ranked.isEmpty()) {
                state.transition(taskId, TaskStatus.NO_CANDIDATES, "No callable hotels found");
                finish(taskId, TaskOutcome.NO_ELIGIBLE_HOTELS);
                return;
            }
            state.transition(taskId, TaskStatus.RANKING, ranked.size() + " eligible hotels ranked");
            task = state.transition(taskId, TaskStatus.CALLING_FOR_QUOTES, "Calling the most promising hotels");
            if (scheduler.startNextBatch(task, ranked) == 0) {
                selectOffer(taskId);
            }
        } catch (RuntimeException e) {
            log.error("task={} orchestration failed", taskId, e);
        }
    }

    /** Called after each post-call webhook is persisted. */
    public synchronized void onCallFinished(UUID taskId) {
        BookingTask task = tasks.findById(taskId).orElseThrow();
        if (task.status() != TaskStatus.CALLING_FOR_QUOTES || calls.countActive(taskId) > 0) {
            return;
        }
        // Simple stopping rule: keep calling while candidates, call budget and time remain.
        // TODO: stop early when the expected saving of another call is below a configured threshold.
        int started = scheduler.startNextBatch(task, candidates.findEligibleInCallOrder(taskId));
        if (started == 0) {
            selectOffer(taskId);
        }
    }

    /** Stop calling and choose the winner. Also invoked by the deadline watchdog. */
    public synchronized void selectOffer(UUID taskId) {
        BookingTask task = tasks.findById(taskId).orElseThrow();
        if (task.status() != TaskStatus.CALLING_FOR_QUOTES) {
            return;
        }
        Optional<HotelOffer> best = evaluator.best(task, offers.findByTask(taskId));
        if (best.isEmpty()) {
            state.transition(taskId, TaskStatus.NO_VALID_OFFER, "No comparable offer obtained");
            finish(taskId, TaskOutcome.NO_VALID_QUOTE);
            return;
        }
        state.transition(taskId, TaskStatus.OFFER_SELECTED, "Best offer: " + best.get().negotiated().comparableTotal().orElseThrow());
        TaskOutcome outcome = switch (task.authorizationMode()) {
            case ENQUIRY_ONLY -> TaskOutcome.ENQUIRY_COMPLETED;
            case REQUIRE_APPROVAL -> TaskOutcome.QUOTE_RECEIVED;
            case BOOK_IF_WITHIN_LIMITS -> bookingCoordinator.claimBookingAttempt(task, best.get()).isPresent()
                    // Booking call not implemented yet (roadmap phase 5): report honestly instead of claiming a booking.
                    ? TaskOutcome.FOLLOW_UP_REQUIRED
                    : TaskOutcome.QUOTE_RECEIVED;
        };
        finish(taskId, outcome);
    }

    private void finish(UUID taskId, TaskOutcome outcome) {
        state.recordOutcome(taskId, outcome);
        BookingTask task = state.transition(taskId, TaskStatus.EMAIL_PENDING, "Sending the report email");
        Optional<HotelOffer> best = evaluator.best(task, offers.findByTask(taskId));
        String city = municipalities.findById(task.municipalityId()).map(Municipality::name).orElse("");
        ReportInput input = new ReportInput(task, city, outcome, best.orElse(null),
                best.flatMap(o -> hotelIndex.findById(o.hotelId())).orElse(null),
                reservations.findByTask(taskId).orElse(null),
                transcripts.findByTask(taskId),
                candidates.countByTask(taskId),
                calls.findByTask(taskId).size());
        boolean sent = notifications.sendReport(input);
        state.transition(taskId, sent ? TaskStatus.COMPLETED : TaskStatus.EMAIL_FAILED,
                sent ? "Report email accepted by provider" : "Report email failed");
    }
}
