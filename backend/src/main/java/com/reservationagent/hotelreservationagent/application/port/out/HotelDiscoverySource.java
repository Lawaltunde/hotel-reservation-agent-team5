package com.reservationagent.hotelreservationagent.application.port.out;

import com.reservationagent.hotelreservationagent.domain.geography.GeoPoint;
import com.reservationagent.hotelreservationagent.domain.geography.Municipality;
import com.reservationagent.hotelreservationagent.domain.hotel.PhoneNumber;
import com.reservationagent.hotelreservationagent.domain.hotel.PriceTier;
import com.reservationagent.hotelreservationagent.domain.hotel.PropertyType;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Finds hotels in a municipality at task time (Google Places Text Search) to fill the hotel index. */
public interface HotelDiscoverySource {

    /** Completes with an empty list when the source is disabled or finds nothing; never with an error. */
    CompletableFuture<List<DiscoveredHotel>> search(Municipality municipality, double radiusKm);

    /**
     * @param sourceKey stable key of the source record, e.g. {@code google:ChIJ...}
     * @param phone     null if the source has no valid NANP number
     */
    record DiscoveredHotel(String sourceKey, String name, String address, GeoPoint location, PropertyType propertyType,
                           PriceTier priceTier, PhoneNumber phone, boolean operational) {
    }
}
