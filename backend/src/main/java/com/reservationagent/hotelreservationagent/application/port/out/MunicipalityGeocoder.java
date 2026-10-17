package com.reservationagent.hotelreservationagent.application.port.out;

import com.reservationagent.hotelreservationagent.domain.geography.GeoPoint;
import com.reservationagent.hotelreservationagent.domain.geography.Province;

import java.util.Optional;

/**
 * Resolves a place name the municipality table does not know (a smaller town, a typo, or a city
 * elsewhere in Canada) to a Canadian locality.
 */
public interface MunicipalityGeocoder {

    /**
     * @param provinceHint the province the guest named, or {@code null}
     * @return the Canadian locality the name refers to, or empty if none (or the geocoder is disabled)
     */
    Optional<Locality> findCanadianLocality(String name, Province provinceHint);

    record Locality(String name, GeoPoint centre, Province province) {
    }
}
