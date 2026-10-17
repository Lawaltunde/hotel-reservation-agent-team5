package com.reservationagent.hotelreservationagent.infrastructure.client.places;

import com.reservationagent.hotelreservationagent.application.port.out.HotelDiscoverySource;
import com.reservationagent.hotelreservationagent.domain.geography.Municipality;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Used when Google Places is disabled: only hotels already in the index are candidates. */
@Component
@ConditionalOnProperty(name = "hotel-agent.google-places.enabled", havingValue = "false", matchIfMissing = true)
class NoHotelDiscovery implements HotelDiscoverySource {

    @Override
    public CompletableFuture<List<DiscoveredHotel>> search(Municipality municipality, double radiusKm) {
        return CompletableFuture.completedFuture(List.of());
    }
}
