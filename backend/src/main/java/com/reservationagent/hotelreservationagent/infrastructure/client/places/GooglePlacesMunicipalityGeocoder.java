package com.reservationagent.hotelreservationagent.infrastructure.client.places;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.reservationagent.hotelreservationagent.application.port.out.MunicipalityGeocoder;
import com.reservationagent.hotelreservationagent.domain.geography.GeoPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Text Search for "{name}, Ontario, Canada" restricted to localities. Google tolerates typos
 * ("Kingstn" finds Kingston); the result is accepted only if its province is ON.
 */
@Component
@ConditionalOnProperty(name = "hotel-agent.google-places.enabled", havingValue = "true")
class GooglePlacesMunicipalityGeocoder implements MunicipalityGeocoder {

    private static final Logger log = LoggerFactory.getLogger(GooglePlacesMunicipalityGeocoder.class);
    private static final String FIELD_MASK = "places.displayName,places.location,places.addressComponents";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final WebClient client;

    GooglePlacesMunicipalityGeocoder(@Qualifier("googlePlaces") WebClient client) {
        this.client = client;
    }

    @Override
    public Optional<Locality> findOntarioLocality(String name) {
        try {
            SearchTextResponse response = client.post()
                    .uri("/v1/places:searchText")
                    .header("X-Goog-FieldMask", FIELD_MASK)
                    .bodyValue(Map.of("textQuery", name + ", Ontario, Canada", "includedType", "locality",
                            "pageSize", 1, "regionCode", "CA"))
                    .retrieve()
                    .bodyToMono(SearchTextResponse.class)
                    .block(TIMEOUT);
            if (response == null || response.places() == null || response.places().isEmpty()) {
                return Optional.empty();
            }
            Place place = response.places().getFirst();
            boolean inOntario = place.addressComponents() != null && place.addressComponents().stream()
                    .anyMatch(c -> c.types() != null && c.types().contains("administrative_area_level_1")
                            && "ON".equals(c.shortText()));
            if (!inOntario || place.location() == null || place.displayName() == null) {
                return Optional.empty();
            }
            return Optional.of(new Locality(place.displayName().text(),
                    new GeoPoint(place.location().latitude(), place.location().longitude())));
        } catch (RuntimeException e) {
            log.warn("Geocoding '{}' failed: {}", name, e.getMessage());
            return Optional.empty();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SearchTextResponse(List<Place> places) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Place(LocalizedText displayName, LatLng location, List<AddressComponent> addressComponents) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AddressComponent(String longText, String shortText, List<String> types) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LocalizedText(String text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LatLng(double latitude, double longitude) {
    }
}
