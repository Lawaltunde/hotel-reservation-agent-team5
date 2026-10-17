package com.reservationagent.hotelreservationagent.domain.geography;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

/**
 * A Canadian municipality. Only destinations that resolve to one of these are accepted.
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
        BigDecimal municipalAccommodationTaxRate,
        String province
) {

    public static final String PROVINCE_CODE = "CA-ON";

    /** Eastern time: "today" when no destination is known yet. */
    public static final ZoneId DEFAULT_TIME_ZONE = ZoneId.of("America/Toronto");

    /** An Ontario municipality (the seeded rows). */
    public Municipality(UUID id, String name, String censusSubdivisionCode, Set<String> aliases, GeoPoint centre,
                        ZoneId timeZone, BigDecimal municipalAccommodationTaxRate) {
        this(id, name, censusSubdivisionCode, aliases, centre, timeZone, municipalAccommodationTaxRate, "ON");
    }

    public Province provinceOrOntario() {
        return Province.parse(province).orElse(Province.ON);
    }

    public Municipality {
        aliases = aliases == null ? Set.of() : Set.copyOf(aliases);
        province = province == null || province.isBlank() ? "ON" : province;
    }
}
