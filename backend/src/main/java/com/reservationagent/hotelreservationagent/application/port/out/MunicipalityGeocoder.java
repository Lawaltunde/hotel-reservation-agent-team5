package com.reservationagent.hotelreservationagent.application.port.out;

import com.reservationagent.hotelreservationagent.domain.geography.GeoPoint;

import java.util.Optional;

/**
 * Resolves a place name the municipality table does not know (a smaller town, or a typo) to an
 * Ontario locality, so any Ontario city can be searched.
 */
public interface MunicipalityGeocoder {

    /** @return the Ontario locality the name refers to, or empty if none (or the geocoder is disabled) */
    Optional<Locality> findOntarioLocality(String name);

    record Locality(String name, GeoPoint centre) {
    }
}
