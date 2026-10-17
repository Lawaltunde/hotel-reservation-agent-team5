package com.reservationagent.hotelreservationagent.application.port.out;

import com.reservationagent.hotelreservationagent.domain.geography.GeoPoint;
import com.reservationagent.hotelreservationagent.domain.geography.Municipality;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MunicipalityRepository {

    /** Matches name or alias, case- and accent-insensitive. */
    List<Municipality> findByNameOrAlias(String name);

    List<Municipality> searchByPrefix(String prefix, int limit);

    Optional<Municipality> findById(UUID id);

    /** Adds an Ontario municipality found by geocoding, or returns the existing one with that name. */
    Municipality addGeocoded(String name, GeoPoint centre);
}
