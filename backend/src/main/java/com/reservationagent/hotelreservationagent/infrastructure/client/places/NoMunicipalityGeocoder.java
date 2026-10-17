package com.reservationagent.hotelreservationagent.infrastructure.client.places;

import com.reservationagent.hotelreservationagent.application.port.out.MunicipalityGeocoder;
import com.reservationagent.hotelreservationagent.domain.geography.Province;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Used when Google Places is disabled: only municipalities in the table are accepted. */
@Component
@ConditionalOnProperty(name = "hotel-agent.google-places.enabled", havingValue = "false", matchIfMissing = true)
class NoMunicipalityGeocoder implements MunicipalityGeocoder {

    @Override
    public Optional<Locality> findCanadianLocality(String name, Province provinceHint) {
        return Optional.empty();
    }
}
