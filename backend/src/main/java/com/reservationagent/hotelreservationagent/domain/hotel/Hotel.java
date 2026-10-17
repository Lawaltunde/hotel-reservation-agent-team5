package com.reservationagent.hotelreservationagent.domain.hotel;

import com.reservationagent.hotelreservationagent.domain.geography.GeoPoint;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One canonical property in the Ontario hotel index, merged from one or more open-data records.
 *
 * @param phones          ranked best first
 * @param phoneConfidence 0..1, how strongly sources agree on the first phone number
 */
public record Hotel(
        UUID id,
        String name,
        String address,
        UUID municipalityId,
        GeoPoint location,
        PropertyType propertyType,
        Integer stars,
        String brand,
        PriceTier priceTier,
        List<PhoneNumber> phones,
        double phoneConfidence,
        String website,
        boolean operational,
        Instant lastSeenAt
) {

    public Hotel {
        phones = phones == null ? List.of() : List.copyOf(phones);
    }

    public boolean isCallable() {
        return operational && !phones.isEmpty();
    }

    /** Prefers a local front-desk number over a chain toll-free line. */
    public PhoneNumber bestPhone() {
        return phones.stream().filter(p -> !p.isTollFree()).findFirst()
                .orElse(phones.isEmpty() ? null : phones.getFirst());
    }
}
