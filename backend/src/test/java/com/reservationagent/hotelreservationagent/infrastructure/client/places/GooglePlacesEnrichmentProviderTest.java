package com.reservationagent.hotelreservationagent.infrastructure.client.places;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.reservationagent.hotelreservationagent.application.port.out.HotelEnrichmentProvider.Enrichment;
import com.reservationagent.hotelreservationagent.domain.hotel.Hotel;
import com.reservationagent.hotelreservationagent.domain.hotel.PriceTier;
import com.reservationagent.hotelreservationagent.domain.hotel.PropertyType;
import com.reservationagent.hotelreservationagent.infrastructure.config.HotelAgentProperties;
import com.reservationagent.hotelreservationagent.infrastructure.config.TestWebClients;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.reservationagent.hotelreservationagent.support.Fixtures.hotel;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/** Lives in the adapter's package because the adapter is package-private. */
class GooglePlacesEnrichmentProviderTest {

    @RegisterExtension
    static WireMockExtension places = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private static final String PATH = "/v1/places:searchText";

    private GooglePlacesEnrichmentProvider provider;
    private final Hotel hotel = hotel("Lord Elgin Hotel", PropertyType.HOTEL, 4, PriceTier.UPSCALE, 0.8, "+16135550100");

    @BeforeEach
    void setUp() {
        HotelAgentProperties p = TestWebClients.properties(places.baseUrl(), Duration.ofMillis(500));
        provider = new GooglePlacesEnrichmentProvider(TestWebClients.googlePlaces(p), p);
    }

    private Enrichment enrich() throws Exception {
        return provider.enrich(hotel).get(5, TimeUnit.SECONDS);
    }

    @Test
    void sendsAFieldMaskedTextSearchBiasedToTheHotelLocation() throws Exception {
        places.stubFor(post(PATH).willReturn(okJson("{}")));

        enrich();

        places.verify(postRequestedFor(urlEqualTo(PATH))
                .withHeader("X-Goog-Api-Key", equalTo("goog-test-key"))
                .withHeader("X-Goog-FieldMask", equalTo(
                        "places.id,places.displayName,places.formattedAddress,places.internationalPhoneNumber,places.businessStatus"))
                .withRequestBody(equalToJson("""
                        {"textQuery": "Lord Elgin Hotel", "maxResultCount": 1,
                         "locationBias": {"circle": {"center": {"latitude": 45.42, "longitude": -75.69}, "radius": 300.0}}}
                        """)));
    }

    @Test
    void mapsTheFirstPlace() throws Exception {
        places.stubFor(post(PATH).willReturn(okJson("""
                {"places": [
                  {"id": "ChIJ-first", "displayName": {"text": "Lord Elgin Hotel"},
                   "formattedAddress": "100 Elgin St, Ottawa, ON K1P 5K8, Canada",
                   "internationalPhoneNumber": "+1 613-235-3333", "businessStatus": "OPERATIONAL", "rating": 4.4},
                  {"id": "ChIJ-second", "internationalPhoneNumber": "+1 613-555-0199"}
                ]}
                """)));

        assertThat(enrich()).isEqualTo(new Enrichment("ChIJ-first",
                new com.reservationagent.hotelreservationagent.domain.hotel.PhoneNumber("+16132353333"), true,
                "100 Elgin St, Ottawa, ON K1P 5K8, Canada"));
    }

    @ParameterizedTest
    @CsvSource({"CLOSED_PERMANENTLY", "CLOSED_TEMPORARILY"})
    void closedPlaceIsNotOperational(String status) throws Exception {
        places.stubFor(post(PATH).willReturn(okJson("{\"places\":[{\"id\":\"p\",\"businessStatus\":\"" + status + "\"}]}")));
        assertThat(enrich().operational()).isFalse();
    }

    @Test
    void missingFieldsStayUnknown() throws Exception {
        places.stubFor(post(PATH).willReturn(okJson("{\"places\":[{\"id\":\"p\"}]}")));
        assertThat(enrich()).isEqualTo(new Enrichment("p", null, null, null));
    }

    @Test
    void noMatchIsAnEmptyEnrichment() throws Exception {
        places.stubFor(post(PATH).willReturn(okJson("{\"places\":[]}")));
        assertThat(enrich()).isEqualTo(Enrichment.empty());

        places.stubFor(post(PATH).willReturn(okJson("{}")));
        assertThat(enrich()).isEqualTo(Enrichment.empty());
    }

    @Test
    void nonNanpPhoneIsDropped() throws Exception {
        places.stubFor(post(PATH).willReturn(okJson(
                "{\"places\":[{\"id\":\"p\",\"internationalPhoneNumber\":\"+44 20 7946 0958\"}]}")));
        assertThat(enrich().verifiedPhone()).isNull();
    }

    @Test
    void httpErrorFailsTheFuture() {
        places.stubFor(post(PATH).willReturn(aResponse().withStatus(403)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":{\"code\":403,\"status\":\"PERMISSION_DENIED\"}}")));

        assertThatExceptionOfType(ExecutionException.class).isThrownBy(this::enrich);
    }

    @Test
    void slowResponseTimesOut() {
        places.stubFor(post(PATH).willReturn(okJson("{}").withFixedDelay(2_000)));

        assertThatExceptionOfType(ExecutionException.class).isThrownBy(this::enrich);
    }

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
            "+1 613-555-0100,+16135550100",
            "+1 (416) 555-0123,+14165550123",
            "+16135550100,+16135550100",
            "613-555-0100,null",
            "+44 20 7946 0958,null",
            "null,null"})
    void normalisesInternationalNumbers(String international, String expected) {
        var phone = GooglePlacesEnrichmentProvider.toPhone(international);
        assertThat(phone == null ? null : phone.e164()).isEqualTo(expected);
    }
}
