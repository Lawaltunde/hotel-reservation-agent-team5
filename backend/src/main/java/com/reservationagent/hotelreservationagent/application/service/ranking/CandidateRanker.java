package com.reservationagent.hotelreservationagent.application.service.ranking;

import com.reservationagent.hotelreservationagent.domain.candidate.PriceSignal;
import com.reservationagent.hotelreservationagent.domain.hotel.Hotel;
import com.reservationagent.hotelreservationagent.domain.hotel.PriceTier;
import com.reservationagent.hotelreservationagent.domain.offer.TaxInclusion;
import com.reservationagent.hotelreservationagent.domain.shared.Money;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Deterministic call ordering (README §7). This is a scheduling heuristic, not the winner
 * calculation: the winner is chosen only from real phone quotes by {@code OfferEvaluator}.
 */
@Component
public class CandidateRanker {

    /** Input for ranking one hotel. Nullable fields are unknown. */
    public record RankingInput(Hotel hotel, double distanceKm, PriceSignal livePrice, Money historicalNightlyMedian) {
    }

    static final Comparator<RankingInput> CALL_ORDER = Comparator
            // 1. a known all-in live price first, cheapest first
            .comparing(CandidateRanker::knownAllInTotal, Comparator.nullsLast(Comparator.naturalOrder()))
            // 2. our own phone-quote history
            .thenComparing(RankingInput::historicalNightlyMedian, Comparator.nullsLast(Comparator.naturalOrder()))
            // 3. price tier from property type / stars / brand
            .thenComparingInt(input -> tierOf(input.hotel()).rank())
            // 4. most trustworthy phone number
            .thenComparing(input -> input.hotel().phoneConfidence(), Comparator.reverseOrder())
            // 5. front desk over central reservations
            .thenComparing(input -> input.hotel().bestPhone() != null && input.hotel().bestPhone().isTollFree())
            // 6. closest to the city centre
            .thenComparingDouble(RankingInput::distanceKm)
            // stable tie-break so ranking is reproducible
            .thenComparing(input -> input.hotel().id());

    public List<RankingInput> rank(List<RankingInput> inputs) {
        return inputs.stream().sorted(CALL_ORDER).toList();
    }

    private static Money knownAllInTotal(RankingInput input) {
        PriceSignal price = input.livePrice();
        return price != null && price.taxInclusion() == TaxInclusion.INCLUDED ? price.estimatedStayTotal() : null;
    }

    private static PriceTier tierOf(Hotel hotel) {
        if (hotel.priceTier() != null && hotel.priceTier() != PriceTier.UNKNOWN) {
            return hotel.priceTier();
        }
        if (hotel.propertyType() == null) {
            return PriceTier.UNKNOWN;
        }
        return switch (hotel.propertyType()) {
            case MOTEL, HOSTEL, GUEST_HOUSE -> PriceTier.ECONOMY;
            case BED_AND_BREAKFAST, SERVICED_APARTMENT -> PriceTier.MIDSCALE;
            case RESORT -> PriceTier.LUXURY;
            case HOTEL -> hotel.stars() == null ? PriceTier.UNKNOWN
                    : hotel.stars() <= 2 ? PriceTier.ECONOMY
                    : hotel.stars() == 3 ? PriceTier.MIDSCALE
                    : hotel.stars() == 4 ? PriceTier.UPSCALE : PriceTier.LUXURY;
            case OTHER -> PriceTier.UNKNOWN;
        };
    }
}
