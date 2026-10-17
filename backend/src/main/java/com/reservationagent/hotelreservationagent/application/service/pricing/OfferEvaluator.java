package com.reservationagent.hotelreservationagent.application.service.pricing;

import com.reservationagent.hotelreservationagent.domain.offer.HotelOffer;
import com.reservationagent.hotelreservationagent.domain.shared.Money;
import com.reservationagent.hotelreservationagent.domain.task.BookingTask;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Chooses the winning offer. Pure BigDecimal comparison; the LLM never decides this. */
@Component
public class OfferEvaluator {

    private final Clock clock;

    public OfferEvaluator(Clock clock) {
        this.clock = clock;
    }

    /** An offer is comparable only if it matches the stay exactly and every mandatory cost is known. */
    public boolean isComparable(BookingTask task, HotelOffer offer) {
        return offer.availabilityConfirmed()
                && task.stay().checkIn().equals(offer.checkIn())
                && task.stay().checkOut().equals(offer.checkOut())
                && offer.guestCount() >= task.stay().guests()
                && offer.negotiated().comparableTotal().isPresent()
                && (offer.quoteValidUntil() == null || offer.quoteValidUntil().isAfter(clock.instant()));
    }

    public boolean isWithinBudget(BookingTask task, HotelOffer offer) {
        Money max = task.maximumTotal();
        return max == null || offer.negotiated().comparableTotal().map(t -> t.compareTo(max) <= 0).orElse(false);
    }

    public Optional<HotelOffer> best(BookingTask task, List<HotelOffer> offers) {
        return offers.stream()
                .filter(o -> isComparable(task, o) && isWithinBudget(task, o))
                .min(Comparator.comparing((HotelOffer o) -> o.negotiated().comparableTotal().orElseThrow())
                        .thenComparing(HotelOffer::receivedAt));
    }
}
