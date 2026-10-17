package com.reservationagent.hotelreservationagent.application.port.out;

import com.reservationagent.hotelreservationagent.domain.geography.Municipality;
import com.reservationagent.hotelreservationagent.domain.hotel.Hotel;
import com.reservationagent.hotelreservationagent.domain.hotel.PropertyType;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Read access to the pre-built Ontario hotel index (open data, PostGIS). */
public interface HotelIndex {

    List<NearbyHotel> findNear(Municipality municipality, double radiusKm, Set<PropertyType> types, int limit);

    Optional<Hotel> findById(UUID id);

    record NearbyHotel(Hotel hotel, double distanceKm) {
    }
}
