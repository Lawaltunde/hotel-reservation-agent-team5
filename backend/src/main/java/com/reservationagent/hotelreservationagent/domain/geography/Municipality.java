package com.reservationagent.hotelreservationagent.domain.geography;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

/**
 * An Ontario municipality (census subdivision). Only destinations that resolve to one of these
 * are accepted.
 *
 * @param municipalAccommodationTaxRate MAT rate as a fraction (e.g. 0.06), or null if none / unknown
 */
public record Municipality(
        UUID id,
        String name,
        String censusSubdivisionCode,
        Set<String> aliases,
        GeoPoint centre,
        ZoneId timeZone,
        BigDecimal municipalAccommodationTaxRate
) {

    public static final String PROVINCE_CODE = "CA-ON";

    /** Eastern time, used for "today" in Ontario. A few north-western municipalities use Central time. */
    public static final ZoneId DEFAULT_TIME_ZONE = ZoneId.of("America/Toronto");

    public Municipality {
        aliases = aliases == null ? Set.of() : Set.copyOf(aliases);
    }
}
