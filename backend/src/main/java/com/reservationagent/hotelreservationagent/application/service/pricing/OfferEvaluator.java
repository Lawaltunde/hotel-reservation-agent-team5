package com.reservationagent.hotelreservationagent.application.service.pricing;

import com.reservationagent.hotelreservationagent.domain.offer.HotelOffer;
import com.reservationagent.hotelreservationagent.domain.offer.OfferPrice;
import com.reservationagent.hotelreservationagent.domain.shared.Money;
import com.reservationagent.hotelreservationagent.domain.task.BookingTask;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Chooses the winning offer. Pure BigDecimal comparison; the LLM never decides this.
 *
 * <p>Only offers whose availability the hotel confirmed for the asked stay compete. They are ranked
 * by {@link OfferPrice}: tier A (a known all-in total) and tier B (an HST estimate with the municipal
 * accommodation tax unconfirmed) together, but a tier-B winner that beats the cheapest tier-A offer
 * by less than {@code app.pricing.estimate-margin} (default 6%, a typical MAT ceiling) gives way to
 * that tier-A offer, because the unconfirmed tax could close the gap.
 */
@Component
public class OfferEvaluator {

    private final Clock clock;
    private final BigDecimal estimateMargin;

    public OfferEvaluator(Clock clock, @Value("${app.pricing.estimate-margin:0.06}") BigDecimal estimateMargin) {
        this.clock = clock;
        this.estimateMargin = estimateMargin;
    }

    /** The price used for ranking and the report, or empty if the offer states no usable price. */
    public Optional<OfferPrice> price(HotelOffer offer) {
        return offer.negotiated().price();
    }

    /** An offer competes if availability is confirmed for the exact stay, a price is known and the quote is valid. */
    public boolean isComparable(BookingTask task, HotelOffer offer) {
        return offer.availabilityConfirmed()
                && task.stay().checkIn().equals(offer.checkIn())
                && task.stay().checkOut().equals(offer.checkOut())
                && offer.guestCount() >= task.stay().guests()
                && price(offer).isPresent()
                && (offer.quoteValidUntil() == null || offer.quoteValidUntil().isAfter(clock.instant()));
    }

    /** True when some offer could be reported as a confirmed-available option. */
    public boolean hasConfirmedPricedOffer(BookingTask task, List<HotelOffer> offers) {
        return offers.stream().anyMatch(o -> isComparable(task, o));
    }

    public boolean isWithinBudget(BookingTask task, HotelOffer offer) {
        Money max = task.maximumTotal();
        return max == null || price(offer).map(p -> p.amount().compareTo(max) <= 0).orElse(false);
    }

    public Optional<HotelOffer> best(BookingTask task, List<HotelOffer> offers) {
        Comparator<HotelOffer> byPrice = Comparator.comparing((HotelOffer o) -> price(o).orElseThrow().amount())
                .thenComparing(HotelOffer::receivedAt);
        List<HotelOffer> eligible = offers.stream()
                .filter(o -> isComparable(task, o) && isWithinBudget(task, o))
                .sorted(byPrice)
                .toList();
        if (eligible.isEmpty()) {
            return Optional.empty();
        }
        HotelOffer winner = eligible.getFirst();
        if (!price(winner).orElseThrow().estimated()) {
            return Optional.of(winner);
        }
        Money limit = price(winner).orElseThrow().amount().times(BigDecimal.ONE.add(estimateMargin));
        return eligible.stream()
                .filter(o -> !price(o).orElseThrow().estimated())
                .filter(o -> price(o).orElseThrow().amount().compareTo(limit) <= 0)
                .findFirst()
                .or(() -> Optional.of(winner));
    }
}
