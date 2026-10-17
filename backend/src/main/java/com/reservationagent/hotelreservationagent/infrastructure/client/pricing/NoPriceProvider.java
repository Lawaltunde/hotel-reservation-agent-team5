package com.reservationagent.hotelreservationagent.infrastructure.client.pricing;

import com.reservationagent.hotelreservationagent.application.port.out.HotelPriceProvider;
import com.reservationagent.hotelreservationagent.domain.candidate.PriceSignal;
import com.reservationagent.hotelreservationagent.domain.hotel.Hotel;
import com.reservationagent.hotelreservationagent.domain.task.StayDetails;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * No open dataset has live room prices. Replace with a WebClient adapter if a commercial price API
 * (e.g. Expedia Rapid) grants access; ranking falls back to quote history and price tiers until then.
 */
@Component
class NoPriceProvider implements HotelPriceProvider {

    @Override
    public CompletableFuture<Optional<PriceSignal>> priceFor(Hotel hotel, StayDetails stay) {
        return CompletableFuture.completedFuture(Optional.empty());
    }
}
