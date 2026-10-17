package com.reservationagent.hotelreservationagent.infrastructure.client.places;

import com.reservationagent.hotelreservationagent.application.port.out.HotelEnrichmentProvider;
import com.reservationagent.hotelreservationagent.domain.hotel.Hotel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/** Used when Google Places is disabled: candidates rely on open-data phone numbers only. */
@Component
@ConditionalOnProperty(name = "hotel-agent.google-places.enabled", havingValue = "false", matchIfMissing = true)
class NoOpEnrichmentProvider implements HotelEnrichmentProvider {

    @Override
    public CompletableFuture<Enrichment> enrich(Hotel hotel) {
        return CompletableFuture.completedFuture(Enrichment.empty());
    }
}
