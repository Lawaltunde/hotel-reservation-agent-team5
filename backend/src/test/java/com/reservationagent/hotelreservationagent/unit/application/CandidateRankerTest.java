package com.reservationagent.hotelreservationagent.unit.application;

import com.reservationagent.hotelreservationagent.application.service.ranking.CandidateRanker;
import com.reservationagent.hotelreservationagent.application.service.ranking.CandidateRanker.RankingInput;
import com.reservationagent.hotelreservationagent.domain.candidate.PriceSignal;
import com.reservationagent.hotelreservationagent.domain.hotel.DataSource;
import com.reservationagent.hotelreservationagent.domain.hotel.Hotel;
import com.reservationagent.hotelreservationagent.domain.hotel.PriceTier;
import com.reservationagent.hotelreservationagent.domain.hotel.PropertyType;
import com.reservationagent.hotelreservationagent.domain.offer.TaxInclusion;
import com.reservationagent.hotelreservationagent.domain.shared.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static com.reservationagent.hotelreservationagent.support.Fixtures.NOW;
import static com.reservationagent.hotelreservationagent.support.Fixtures.hotel;
import static org.assertj.core.api.Assertions.assertThat;

class CandidateRankerTest {

    private static final String LOCAL = "+16135550100";
    private static final String TOLL_FREE = "+18005550100";

    private final CandidateRanker ranker = new CandidateRanker();

    private static Hotel midscale(String name) {
        return hotel(name, PropertyType.HOTEL, 3, PriceTier.MIDSCALE, 0.8, LOCAL);
    }

    private static RankingInput input(Hotel hotel) {
        return new RankingInput(hotel, 2.0, null, null);
    }

    private static PriceSignal live(String total, TaxInclusion inclusion) {
        return new PriceSignal(Money.cad(total), inclusion, DataSource.GOOGLE_PLACES, NOW);
    }

    private List<String> order(RankingInput... inputs) {
        List<RankingInput> shuffled = new ArrayList<>(List.of(inputs));
        Collections.reverse(shuffled);
        return ranker.rank(shuffled).stream().map(i -> i.hotel().name()).toList();
    }

    @Test
    void knownAllInLivePriceComesFirstCheapestFirst() {
        RankingInput cheapLive = new RankingInput(midscale("cheap-live"), 9, live("180", TaxInclusion.INCLUDED), null);
        RankingInput dearLive = new RankingInput(midscale("dear-live"), 1, live("250", TaxInclusion.INCLUDED), null);
        RankingInput economyNoPrice = input(hotel("economy", PropertyType.MOTEL, null, PriceTier.ECONOMY, 1, LOCAL));

        assertThat(order(economyNoPrice, dearLive, cheapLive)).containsExactly("cheap-live", "dear-live", "economy");
    }

    @Test
    void livePriceThatExcludesTaxesIsNotTreatedAsAllIn() {
        RankingInput excluded = new RankingInput(hotel("excl", PropertyType.HOTEL, 5, PriceTier.LUXURY, 1, LOCAL), 1,
                live("50", TaxInclusion.EXCLUDED), null);
        RankingInput unknown = new RankingInput(hotel("unk", PropertyType.HOTEL, 5, PriceTier.LUXURY, 1, LOCAL), 1,
                live("40", TaxInclusion.UNKNOWN), null);
        RankingInput economy = input(hotel("economy", PropertyType.MOTEL, null, PriceTier.ECONOMY, 1, LOCAL));

        assertThat(order(excluded, unknown, economy).getFirst()).isEqualTo("economy");
    }

    @Test
    void quoteHistoryBeatsPriceTier() {
        RankingInput history = new RankingInput(hotel("history", PropertyType.HOTEL, 4, PriceTier.UPSCALE, 0.5, LOCAL),
                5, null, Money.cad("140"));
        RankingInput dearerHistory = new RankingInput(hotel("dearer", PropertyType.HOTEL, 4, PriceTier.UPSCALE, 0.5, LOCAL),
                5, null, Money.cad("160"));
        RankingInput economy = input(hotel("economy", PropertyType.MOTEL, null, PriceTier.ECONOMY, 1, LOCAL));

        assertThat(order(economy, dearerHistory, history)).containsExactly("history", "dearer", "economy");
    }

    @Test
    void cheaperTierFirst() {
        RankingInput lux = input(hotel("lux", PropertyType.HOTEL, null, PriceTier.LUXURY, 1, LOCAL));
        RankingInput up = input(hotel("up", PropertyType.HOTEL, null, PriceTier.UPSCALE, 1, LOCAL));
        RankingInput mid = input(hotel("mid", PropertyType.HOTEL, null, PriceTier.MIDSCALE, 1, LOCAL));
        RankingInput eco = input(hotel("eco", PropertyType.HOTEL, null, PriceTier.ECONOMY, 1, LOCAL));
        RankingInput unknown = input(hotel("unknown", PropertyType.OTHER, null, PriceTier.UNKNOWN, 1, LOCAL));

        assertThat(order(unknown, lux, up, mid, eco)).containsExactly("eco", "mid", "up", "lux", "unknown");
    }

    /** With no explicit tier, the tier is inferred from property type and stars. */
    @ParameterizedTest(name = "{0} stars={1} ranks like {2}")
    @CsvSource(nullValues = "null", value = {
            "MOTEL,null,ECONOMY", "HOSTEL,null,ECONOMY", "GUEST_HOUSE,null,ECONOMY",
            "BED_AND_BREAKFAST,null,MIDSCALE", "SERVICED_APARTMENT,null,MIDSCALE", "RESORT,null,LUXURY",
            "HOTEL,1,ECONOMY", "HOTEL,2,ECONOMY", "HOTEL,3,MIDSCALE", "HOTEL,4,UPSCALE", "HOTEL,5,LUXURY",
            "HOTEL,null,UNKNOWN", "OTHER,null,UNKNOWN"})
    void infersTheTier(PropertyType type, Integer stars, PriceTier expected) {
        RankingInput inferred = input(hotel("inferred", type, stars, PriceTier.UNKNOWN, 0.5, LOCAL));
        // Same tier, so the next criterion decides: the more trusted phone goes first. A wrongly
        // cheaper inferred tier would put "inferred" first; the check below catches a dearer one.
        RankingInput explicitMoreTrusted = input(hotel("explicit+", PropertyType.OTHER, null, expected, 0.6, LOCAL));

        assertThat(order(inferred, explicitMoreTrusted)).containsExactly("explicit+", "inferred");
        if (expected != PriceTier.ECONOMY) {
            RankingInput cheaper = input(hotel("cheaper", PropertyType.OTHER, null, PriceTier.ECONOMY, 0, LOCAL));
            assertThat(order(inferred, cheaper)).containsExactly("cheaper", "inferred");
        }
    }

    @Test
    void nullPropertyTypeWithoutTierRanksAsUnknown() {
        RankingInput nullType = input(hotel("null-type", null, null, null, 1, LOCAL));
        RankingInput luxury = input(hotel("lux", PropertyType.RESORT, null, PriceTier.UNKNOWN, 0, LOCAL));
        assertThat(order(nullType, luxury)).containsExactly("lux", "null-type");
    }

    @Test
    void withinATierMoreTrustedPhoneFirstThenFrontDeskThenCloser() {
        RankingInput trusted = new RankingInput(hotel("trusted", PropertyType.HOTEL, 3, PriceTier.MIDSCALE, 0.95, TOLL_FREE), 9, null, null);
        RankingInput frontDeskFar = new RankingInput(hotel("front-desk-far", PropertyType.HOTEL, 3, PriceTier.MIDSCALE, 0.7, LOCAL), 8, null, null);
        RankingInput frontDeskNear = new RankingInput(hotel("front-desk-near", PropertyType.HOTEL, 3, PriceTier.MIDSCALE, 0.7, LOCAL), 1, null, null);
        RankingInput tollFreeNear = new RankingInput(hotel("toll-free-near", PropertyType.HOTEL, 3, PriceTier.MIDSCALE, 0.7, TOLL_FREE), 0.5, null, null);

        assertThat(order(tollFreeNear, frontDeskFar, frontDeskNear, trusted))
                .containsExactly("trusted", "front-desk-near", "front-desk-far", "toll-free-near");
    }

    @Test
    void identicalHotelsAreOrderedByIdSoRankingIsReproducible() {
        List<RankingInput> inputs = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            inputs.add(input(midscale("h" + i)));
        }
        List<UUID> first = ranker.rank(inputs).stream().map(i -> i.hotel().id()).toList();
        Collections.shuffle(inputs);
        List<UUID> second = ranker.rank(inputs).stream().map(i -> i.hotel().id()).toList();

        assertThat(second).isEqualTo(first).isSorted();
    }

    @Test
    void emptyInputGivesEmptyRanking() {
        assertThat(ranker.rank(List.of())).isEmpty();
    }
}
