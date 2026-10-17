package com.reservationagent.hotelreservationagent.application.service.discovery;

import com.reservationagent.hotelreservationagent.application.policy.AgentPolicy;
import com.reservationagent.hotelreservationagent.application.port.out.HotelCandidateRepository;
import com.reservationagent.hotelreservationagent.application.port.out.HotelEnrichmentProvider;
import com.reservationagent.hotelreservationagent.application.port.out.HotelIndex;
import com.reservationagent.hotelreservationagent.application.port.out.HotelIndex.NearbyHotel;
import com.reservationagent.hotelreservationagent.application.port.out.HotelPriceProvider;
import com.reservationagent.hotelreservationagent.application.port.out.QuoteHistoryRepository;
import com.reservationagent.hotelreservationagent.application.service.ranking.CandidateRanker;
import com.reservationagent.hotelreservationagent.application.service.ranking.CandidateRanker.RankingInput;
import com.reservationagent.hotelreservationagent.domain.candidate.ExclusionReason;
import com.reservationagent.hotelreservationagent.domain.candidate.HotelCandidate;
import com.reservationagent.hotelreservationagent.domain.candidate.PriceSignal;
import com.reservationagent.hotelreservationagent.domain.geography.Municipality;
import com.reservationagent.hotelreservationagent.domain.hotel.Hotel;
import com.reservationagent.hotelreservationagent.domain.shared.Money;
import com.reservationagent.hotelreservationagent.domain.task.BookingTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Local index query, hard filters, live enrichment of the shortlist only, then deterministic ranking.
 */
@Service
public class CandidateDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(CandidateDiscoveryService.class);

    private final HotelIndex hotelIndex;
    private final HotelEnrichmentProvider enrichment;
    private final HotelPriceProvider prices;
    private final QuoteHistoryRepository quoteHistory;
    private final CandidateRanker ranker;
    private final HotelCandidateRepository candidates;
    private final AgentPolicy policy;

    public CandidateDiscoveryService(HotelIndex hotelIndex, HotelEnrichmentProvider enrichment, HotelPriceProvider prices,
                                     QuoteHistoryRepository quoteHistory, CandidateRanker ranker,
                                     HotelCandidateRepository candidates, AgentPolicy policy) {
        this.hotelIndex = hotelIndex;
        this.enrichment = enrichment;
        this.prices = prices;
        this.quoteHistory = quoteHistory;
        this.ranker = ranker;
        this.candidates = candidates;
        this.policy = policy;
    }

    /** @return eligible candidates in call order (also persisted, with exclusions) */
    public List<HotelCandidate> discover(BookingTask task, Municipality municipality) {
        List<NearbyHotel> nearby = hotelIndex.findNear(
                municipality, policy.searchRadiusKm(), policy.allowedPropertyTypes(), policy.candidateLimit());

        List<HotelCandidate> excluded = new ArrayList<>();
        List<NearbyHotel> callable = new ArrayList<>();
        for (NearbyHotel n : nearby) {
            ExclusionReason reason = !n.hotel().operational() ? ExclusionReason.NOT_OPERATIONAL
                    : n.hotel().phones().isEmpty() ? ExclusionReason.NO_PHONE_NUMBER : null;
            if (reason == null) {
                callable.add(n);
            } else {
                excluded.add(candidate(task, n, null, null, reason));
            }
        }

        Set<UUID> ids = callable.stream().map(n -> n.hotel().id()).collect(Collectors.toSet());
        Map<UUID, Money> history = quoteHistory.medianNightlyTotals(ids);

        // First pass on open data + history, then spend live API calls on the shortlist only.
        List<RankingInput> firstPass = ranker.rank(callable.stream()
                .map(n -> new RankingInput(n.hotel(), n.distanceKm(), null, history.get(n.hotel().id())))
                .toList());
        List<RankingInput> shortlist = firstPass.stream().limit(policy.enrichmentLimit()).toList();
        List<RankingInput> enriched = enrichShortlist(task, shortlist);

        List<RankingInput> ranked = new ArrayList<>(ranker.rank(enriched));
        ranked.addAll(firstPass.subList(shortlist.size(), firstPass.size()));

        List<HotelCandidate> result = new ArrayList<>();
        int priority = 1;
        for (RankingInput input : ranked) {
            boolean overBudget = task.maximumTotal() != null && input.livePrice() != null
                    && input.livePrice().estimatedStayTotal().compareTo(task.maximumTotal()) > 0;
            if (overBudget) {
                excluded.add(candidate(task, input.hotel(), input.distanceKm(), input.livePrice(), null, ExclusionReason.OVER_BUDGET));
            } else {
                result.add(candidate(task, input.hotel(), input.distanceKm(), input.livePrice(), priority++, null));
            }
        }
        List<HotelCandidate> all = new ArrayList<>(result);
        all.addAll(excluded);
        candidates.saveAll(all);
        log.info("task={} screened={} eligible={}", task.id(), nearby.size(), result.size());
        return result;
    }

    private List<RankingInput> enrichShortlist(BookingTask task, List<RankingInput> shortlist) {
        long timeoutMs = policy.enrichmentTimeout().toMillis();
        List<CompletableFuture<RankingInput>> futures = shortlist.stream().map(input -> {
            CompletableFuture<HotelEnrichmentProvider.Enrichment> details = enrichment.enrich(input.hotel())
                    .completeOnTimeout(HotelEnrichmentProvider.Enrichment.empty(), timeoutMs, TimeUnit.MILLISECONDS)
                    .exceptionally(ex -> HotelEnrichmentProvider.Enrichment.empty());
            CompletableFuture<Optional<PriceSignal>> price = prices.priceFor(input.hotel(), task.stay())
                    .completeOnTimeout(Optional.empty(), timeoutMs, TimeUnit.MILLISECONDS)
                    .exceptionally(ex -> Optional.empty());
            return details.thenCombine(price, (d, p) ->
                    new RankingInput(applyEnrichment(input.hotel(), d), input.distanceKm(), p.orElse(null),
                            input.historicalNightlyMedian()));
        }).toList();
        return futures.stream().map(CompletableFuture::join)
                .filter(input -> input.hotel().isCallable())
                .toList();
    }

    private static Hotel applyEnrichment(Hotel hotel, HotelEnrichmentProvider.Enrichment e) {
        if (e.verifiedPhone() == null && e.operational() == null) {
            return hotel;
        }
        var phones = new ArrayList<>(hotel.phones());
        if (e.verifiedPhone() != null) {
            phones.remove(e.verifiedPhone());
            phones.addFirst(e.verifiedPhone());
        }
        return new Hotel(hotel.id(), hotel.name(), e.formattedAddress() != null ? e.formattedAddress() : hotel.address(),
                hotel.municipalityId(), hotel.location(), hotel.propertyType(), hotel.stars(), hotel.brand(),
                hotel.priceTier(), phones, e.verifiedPhone() != null ? 1.0 : hotel.phoneConfidence(), hotel.website(),
                e.operational() != null ? e.operational() : hotel.operational(), hotel.lastSeenAt());
    }

    private static HotelCandidate candidate(BookingTask task, NearbyHotel n, PriceSignal price, Integer priority,
                                            ExclusionReason reason) {
        return candidate(task, n.hotel(), n.distanceKm(), price, priority, reason);
    }

    private static HotelCandidate candidate(BookingTask task, Hotel hotel, double distanceKm, PriceSignal price,
                                            Integer priority, ExclusionReason reason) {
        return new HotelCandidate(UUID.randomUUID(), task.id(), hotel.id(), price, distanceKm, priority, reason);
    }
}
