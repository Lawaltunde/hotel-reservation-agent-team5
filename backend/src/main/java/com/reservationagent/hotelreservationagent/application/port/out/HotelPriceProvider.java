package com.reservationagent.hotelreservationagent.application.port.out;

import com.reservationagent.hotelreservationagent.domain.candidate.PriceSignal;
import com.reservationagent.hotelreservationagent.domain.hotel.Hotel;
import com.reservationagent.hotelreservationagent.domain.task.StayDetails;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Optional date-specific price hint. Used for ranking only, never as a quote. */
public interface HotelPriceProvider {

    CompletableFuture<Optional<PriceSignal>> priceFor(Hotel hotel, StayDetails stay);
}
