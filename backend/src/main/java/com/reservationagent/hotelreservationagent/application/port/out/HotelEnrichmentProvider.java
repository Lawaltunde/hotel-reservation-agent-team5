package com.reservationagent.hotelreservationagent.application.port.out;

import com.reservationagent.hotelreservationagent.domain.hotel.Hotel;
import com.reservationagent.hotelreservationagent.domain.hotel.PhoneNumber;

import java.util.concurrent.CompletableFuture;

/** Live verification of a shortlisted hotel (e.g. Google Places). Called for the top N only. */
public interface HotelEnrichmentProvider {

    CompletableFuture<Enrichment> enrich(Hotel hotel);

    record Enrichment(String providerPlaceId, PhoneNumber verifiedPhone, Boolean operational, String formattedAddress) {

        public static Enrichment empty() {
            return new Enrichment(null, null, null, null);
        }
    }
}
