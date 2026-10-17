package com.reservationagent.hotelreservationagent.infrastructure.client.places;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.reservationagent.hotelreservationagent.application.port.out.HotelDiscoverySource;
import com.reservationagent.hotelreservationagent.domain.geography.GeoPoint;
import com.reservationagent.hotelreservationagent.domain.geography.Municipality;
import com.reservationagent.hotelreservationagent.domain.hotel.PriceTier;
import com.reservationagent.hotelreservationagent.domain.hotel.PropertyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * "hotels in {city}, {province}" via Google Places API (New) Text Search, biased to the municipality.
 * Only the fields in the mask are requested (and billed). The hotels table is empty until open-data
 * ingestion exists, so this is how a task gets candidates today.
 * <p>
 * TODO(Google terms): Places content other than the place ID may be cached for at most 30 days;
 * add expiry/refresh before relying on rows older than that.
 */
@Component
@ConditionalOnProperty(name = "hotel-agent.google-places.enabled", havingValue = "true")
class GooglePlacesHotelDiscovery implements HotelDiscoverySource {

    private static final Logger log = LoggerFactory.getLogger(GooglePlacesHotelDiscovery.class);
    private static final String FIELD_MASK = "places.id,places.displayName,places.formattedAddress,"
            + "places.nationalPhoneNumber,places.internationalPhoneNumber,places.location,places.rating,"
            + "places.priceLevel,places.businessStatus,places.types";
    /** Text Search accepts a bias radius of at most 50 km. */
    private static final double MAX_BIAS_METRES = 50_000;
    private static final int PAGE_SIZE = 20;

    private final WebClient client;

    GooglePlacesHotelDiscovery(@Qualifier("googlePlaces") WebClient client) {
        this.client = client;
    }

    @Override
    public CompletableFuture<List<DiscoveredHotel>> search(Municipality municipality, double radiusKm) {
        Map<String, Object> body = Map.of(
                "textQuery", "hotels in " + municipality.name() + ", " + municipality.provinceOrOntario().displayName(),
                "includedType", "lodging",
                "pageSize", PAGE_SIZE,
                "regionCode", "CA",
                "locationBias", Map.of("circle", Map.of(
                        "center", Map.of("latitude", municipality.centre().latitude(),
                                "longitude", municipality.centre().longitude()),
                        "radius", Math.min(radiusKm * 1000, MAX_BIAS_METRES))));
        return client.post()
                .uri("/v1/places:searchText")
                .header("X-Goog-FieldMask", FIELD_MASK)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(SearchTextResponse.class)
                .map(response -> response.places() == null ? List.<DiscoveredHotel>of()
                        : response.places().stream().map(GooglePlacesHotelDiscovery::toHotel).toList())
                .defaultIfEmpty(List.of())
                .onErrorResume(e -> {
                    log.warn("Places hotel search for {} failed: {}", municipality.name(), e.getMessage());
                    return reactor.core.publisher.Mono.just(List.of());
                })
                .toFuture();
    }

    private static DiscoveredHotel toHotel(Place p) {
        String phone = p.internationalPhoneNumber() != null ? p.internationalPhoneNumber()
                : p.nationalPhoneNumber() == null ? null : "+1 " + p.nationalPhoneNumber();
        return new DiscoveredHotel(
                "google:" + p.id(),
                p.displayName() == null || p.displayName().text() == null ? "Unnamed hotel" : p.displayName().text(),
                p.formattedAddress(),
                p.location() == null ? null : new GeoPoint(p.location().latitude(), p.location().longitude()),
                propertyType(p.types()),
                priceTier(p.priceLevel()),
                GooglePlacesEnrichmentProvider.toPhone(phone),
                p.businessStatus() == null || "OPERATIONAL".equals(p.businessStatus()));
    }

    private static PropertyType propertyType(List<String> types) {
        if (types == null) return PropertyType.HOTEL;
        if (types.contains("motel")) return PropertyType.MOTEL;
        if (types.contains("bed_and_breakfast")) return PropertyType.BED_AND_BREAKFAST;
        if (types.contains("guest_house")) return PropertyType.GUEST_HOUSE;
        if (types.contains("hostel")) return PropertyType.HOSTEL;
        if (types.contains("resort_hotel")) return PropertyType.RESORT;
        if (types.contains("campground") || types.contains("rv_park")) return PropertyType.OTHER;
        return PropertyType.HOTEL;
    }

    private static PriceTier priceTier(String level) {
        if (level == null) return PriceTier.UNKNOWN;
        return switch (level) {
            case "PRICE_LEVEL_INEXPENSIVE" -> PriceTier.ECONOMY;
            case "PRICE_LEVEL_MODERATE" -> PriceTier.MIDSCALE;
            case "PRICE_LEVEL_EXPENSIVE" -> PriceTier.UPSCALE;
            case "PRICE_LEVEL_VERY_EXPENSIVE" -> PriceTier.LUXURY;
            default -> PriceTier.UNKNOWN;
        };
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SearchTextResponse(List<Place> places) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Place(String id, LocalizedText displayName, String formattedAddress, String nationalPhoneNumber,
                 String internationalPhoneNumber, LatLng location, Double rating, String priceLevel,
                 String businessStatus, List<String> types) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LocalizedText(String text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LatLng(double latitude, double longitude) {
    }
}
