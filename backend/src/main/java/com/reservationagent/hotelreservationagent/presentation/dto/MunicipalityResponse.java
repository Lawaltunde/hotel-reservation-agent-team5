package com.reservationagent.hotelreservationagent.presentation.dto;

import com.reservationagent.hotelreservationagent.domain.geography.Municipality;
import java.util.UUID;

/** One city-field suggestion from {@code GET /api/municipalities}. */
public record MunicipalityResponse(UUID id, String name, String timeZone) {

    public static MunicipalityResponse from(Municipality m) {
        return new MunicipalityResponse(m.id(), m.name(), m.timeZone().getId());
    }
}
