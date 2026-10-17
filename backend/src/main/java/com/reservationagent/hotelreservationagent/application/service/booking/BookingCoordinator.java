package com.reservationagent.hotelreservationagent.application.service.booking;

import com.reservationagent.hotelreservationagent.application.port.out.ReservationRepository;
import com.reservationagent.hotelreservationagent.application.service.pricing.OfferEvaluator;
import com.reservationagent.hotelreservationagent.domain.offer.HotelOffer;
import com.reservationagent.hotelreservationagent.domain.reservation.Reservation;
import com.reservationagent.hotelreservationagent.domain.reservation.ReservationState;
import com.reservationagent.hotelreservationagent.domain.task.BookingTask;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * The single authority allowed to start a reservation attempt (README §9). Concurrent quote calls
 * never book; this coordinator claims the one reservation row per task before any booking call.
 */
@Service
public class BookingCoordinator {

    private final ReservationRepository reservations;
    private final OfferEvaluator evaluator;
    private final Clock clock;

    public BookingCoordinator(ReservationRepository reservations, OfferEvaluator evaluator, Clock clock) {
        this.reservations = reservations;
        this.evaluator = evaluator;
        this.clock = clock;
    }

    /**
     * @return the claimed reservation in ATTEMPT_IN_PROGRESS, or empty if booking is not allowed
     * or a reservation already exists for this task
     */
    @Transactional
    public Optional<Reservation> claimBookingAttempt(BookingTask task, HotelOffer offer) {
        if (!task.authorizationMode().allowsBooking()) {
            return Optional.empty();
        }
        // Booking needs a firm all-in total, never an estimate.
        if (!evaluator.isComparable(task, offer) || !evaluator.isWithinBudget(task, offer)
                || offer.negotiated().comparableTotal().isEmpty()) {
            return Optional.empty();
        }
        Reservation attempt = new Reservation(UUID.randomUUID(), task.id(), offer.hotelId(), offer.id(), null,
                offer.negotiated().comparableTotal().orElseThrow(), ReservationState.ATTEMPT_IN_PROGRESS,
                offer.evidenceStatus(), clock.instant());
        return reservations.claim(attempt) ? Optional.of(attempt) : Optional.empty();
    }

    // TODO(phase 5): place the booking call through VoiceCallProvider with a booking-specific agent,
    //  then confirm / fail / mark UNCERTAIN from the post-call webhook. Never retry an UNCERTAIN attempt
    //  before reconciling it.
}
