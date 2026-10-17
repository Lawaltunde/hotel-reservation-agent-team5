package com.reservationagent.hotelreservationagent.infrastructure.client.places;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.reservationagent.hotelreservationagent.application.port.out.HotelEnrichmentProvider;
import com.reservationagent.hotelreservationagent.domain.hotel.Hotel;
import com.reservationagent.hotelreservationagent.domain.hotel.PhoneNumber;
import com.reservationagent.hotelreservationagent.domain.shared.DomainException;
import com.reservationagent.hotelreservationagent.infrastructure.config.HotelAgentProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Verifies a shortlisted hotel with Google Places API (New) Text Search, using a field mask so
 * only the fields we need are billed and returned. Google content is not merged into the open-data index.
 */
@Component
@ConditionalOnProperty(name = "hotel-agent.google-places.enabled", havingValue = "true")
class GooglePlacesEnrichmentProvider implements HotelEnrichmentProvider {

    private static final String FIELD_MASK =
            "places.id,places.displayName,places.formattedAddress,places.internationalPhoneNumber,places.businessStatus";

    private final WebClient client;
    private final double matchRadiusMetres;

    GooglePlacesEnrichmentProvider(@Qualifier("googlePlaces") WebClient client, HotelAgentProperties properties) {
        this.client = client;
        this.matchRadiusMetres = properties.googlePlaces().matchRadiusMetres();
    }

    @Override
    public CompletableFuture<Enrichment> enrich(Hotel hotel) {
        Map<String, Object> body = Map.of(
                "textQuery", hotel.name(),
                "maxResultCount", 1,
                "locationBias", Map.of("circle", Map.of(
                        "center", Map.of("latitude", hotel.location().latitude(), "longitude", hotel.location().longitude()),
                        "radius", matchRadiusMetres)));
        return client.post()
                .uri("/v1/places:searchText")
                .header("X-Goog-FieldMask", FIELD_MASK)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(SearchTextResponse.class)
                .map(GooglePlacesEnrichmentProvider::toEnrichment)
                .defaultIfEmpty(Enrichment.empty())
                .toFuture();
    }

    private static Enrichment toEnrichment(SearchTextResponse response) {
        if (response.places() == null || response.places().isEmpty()) {
            return Enrichment.empty();
        }
        Place place = response.places().getFirst();
        return new Enrichment(place.id(), toPhone(place.internationalPhoneNumber()),
                place.businessStatus() == null ? null : "OPERATIONAL".equals(place.businessStatus()),
                place.formattedAddress());
    }

    /** "+1 613-555-0100" -> "+16135550100"; anything that is not a valid NANP number is dropped. */
    static PhoneNumber toPhone(String international) {
        if (international == null) return null;
        String digits = international.replaceAll("[^0-9]", "");
        try {
            return new PhoneNumber("+" + digits);
        } catch (DomainException e) {
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SearchTextResponse(List<Place> places) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Place(String id, String formattedAddress, String internationalPhoneNumber, String businessStatus) {
    }
}
