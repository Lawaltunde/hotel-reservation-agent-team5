package com.reservationagent.hotelreservationagent.application.service.pricing;

import com.reservationagent.hotelreservationagent.application.port.out.BookingTaskRepository;
import com.reservationagent.hotelreservationagent.application.port.out.HotelCandidateRepository;
import com.reservationagent.hotelreservationagent.application.port.out.MunicipalityRepository;
import com.reservationagent.hotelreservationagent.domain.candidate.HotelCandidate;
import com.reservationagent.hotelreservationagent.domain.offer.HotelOffer;
import com.reservationagent.hotelreservationagent.domain.offer.PriceBreakdown;
import com.reservationagent.hotelreservationagent.domain.offer.OfferPrice;
import com.reservationagent.hotelreservationagent.domain.shared.Money;
import com.reservationagent.hotelreservationagent.domain.task.BookingTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Chooses the winning offer. Pure BigDecimal comparison; the LLM never decides this.
 *
 * <p>Only offers whose availability the hotel confirmed for the asked stay compete. They are ranked
 * by {@link OfferPrice}: tier A (a known all-in total) and tier B (an HST estimate with the municipal
 * accommodation tax unconfirmed) together, but a tier-B winner that beats the cheapest tier-A offer
 * by less than {@code app.pricing.estimate-margin} (default 6%, a typical MAT ceiling) gives way to
 * that tier-A offer, because the unconfirmed levy could close the gap. Tier B uses the destination
 * province's sales tax on accommodation. Among equal prices the hotel closest to downtown wins.
 */
@Component
public class OfferEvaluator {

    private final Clock clock;
    private final BigDecimal estimateMargin;
    private final Function<UUID, BigDecimal> salesTaxRateByTask;
    private final Function<UUID, Map<UUID, Double>> distanceByHotelByTask;

    /** Ontario sales tax and no distance tiebreak (tests). */
    public OfferEvaluator(Clock clock, BigDecimal estimateMargin) {
        this(clock, estimateMargin, task -> PriceBreakdown.ONTARIO_HST_RATE, task -> Map.of());
    }

    public OfferEvaluator(Clock clock, BigDecimal estimateMargin, Function<UUID, BigDecimal> salesTaxRateByTask,
                          Function<UUID, Map<UUID, Double>> distanceByHotelByTask) {
        this.clock = clock;
        this.estimateMargin = estimateMargin;
        this.salesTaxRateByTask = salesTaxRateByTask;
        this.distanceByHotelByTask = distanceByHotelByTask;
    }

    /** Wired with the destination province's sales tax and each hotel's distance from downtown. */
    @Autowired
    public OfferEvaluator(Clock clock, @Value("${app.pricing.estimate-margin:0.06}") BigDecimal estimateMargin,
                          BookingTaskRepository tasks, MunicipalityRepository municipalities,
                          HotelCandidateRepository candidates) {
        this(clock, estimateMargin,
                taskId -> tasks.findById(taskId)
                        .flatMap(t -> municipalities.findById(t.municipalityId()))
                        .map(m -> m.provinceOrOntario().salesTaxRate())
                        .orElse(PriceBreakdown.ONTARIO_HST_RATE),
                taskId -> candidates.findEligibleInCallOrder(taskId).stream()
                        .collect(Collectors.toMap(HotelCandidate::hotelId, HotelCandidate::distanceKm, (a, b) -> a)));
    }

    /** The price used for ranking and the report, or empty if the offer states no usable price. */
    public Optional<OfferPrice> price(HotelOffer offer) {
        return offer.negotiated().price(salesTaxRateByTask.apply(offer.taskId()));
    }

    /** Distance of the offer's hotel from the municipality centre ("downtown"), if known. */
    public Optional<Double> distanceKm(HotelOffer offer) {
        return Optional.ofNullable(distanceByHotelByTask.apply(offer.taskId()).get(offer.hotelId()));
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
        // Equal prices go to the hotel closest to downtown (the organizer's tiebreak), then the earlier quote.
        Map<UUID, Double> distances = offers.isEmpty() ? Map.of() : distanceByHotelByTask.apply(task.id());
        Comparator<HotelOffer> byPrice = Comparator.comparing((HotelOffer o) -> price(o).orElseThrow().amount())
                .thenComparing(o -> distances.get(o.hotelId()), Comparator.nullsLast(Comparator.naturalOrder()))
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
