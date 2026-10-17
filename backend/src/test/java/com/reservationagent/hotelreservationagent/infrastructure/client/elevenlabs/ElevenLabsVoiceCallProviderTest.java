package com.reservationagent.hotelreservationagent.infrastructure.client.elevenlabs;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.reservationagent.hotelreservationagent.application.port.out.VoiceCallProvider.StartedCall;
import com.reservationagent.hotelreservationagent.domain.hotel.PhoneNumber;
import com.reservationagent.hotelreservationagent.infrastructure.config.HotelAgentProperties;
import com.reservationagent.hotelreservationagent.infrastructure.config.TestWebClients;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/** Lives in the adapter's package because the adapter is package-private. */
class ElevenLabsVoiceCallProviderTest {

    @RegisterExtension
    static WireMockExtension elevenLabs = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private static final String PATH = "/v1/convai/twilio/outbound-call";
    private static final PhoneNumber HOTEL = new PhoneNumber("+16135550100");

    private ElevenLabsVoiceCallProvider provider;
    private final Map<String, String> variables = new LinkedHashMap<>(Map.of(
            "hotel_name", "Rideau Inn", "check_in", "2026-11-20", "check_out", "2026-11-22", "guests", "2"));

    @BeforeEach
    void setUp() {
        HotelAgentProperties p = TestWebClients.properties(elevenLabs.baseUrl(), Duration.ofMillis(500));
        provider = new ElevenLabsVoiceCallProvider(TestWebClients.elevenLabs(p), p);
    }

    private StartedCall call() throws Exception {
        return provider.startCall(HOTEL, variables).get(5, TimeUnit.SECONDS);
    }

    @Test
    void startsAnOutboundTwilioCallWithTheAgentAndDynamicVariables() throws Exception {
        elevenLabs.stubFor(post(PATH).willReturn(okJson("""
                {"success": true, "message": "Success", "conversation_id": "conv_123", "callSid": "CA456"}
                """)));

        assertThat(call()).isEqualTo(new StartedCall("CA456", "conv_123"));

        elevenLabs.verify(postRequestedFor(urlEqualTo(PATH))
                .withHeader("xi-api-key", equalTo("xi-test-key"))
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(equalToJson("""
                        {"agent_id": "agent_test", "agent_phone_number_id": "phnum_test", "to_number": "+16135550100",
                         "conversation_initiation_client_data": {"dynamic_variables":
                           {"hotel_name": "Rideau Inn", "check_in": "2026-11-20", "check_out": "2026-11-22", "guests": "2"}}}
                        """)));
    }

    @Test
    void refusalInA200BodyFailsTheFuture() {
        elevenLabs.stubFor(post(PATH).willReturn(okJson("""
                {"success": false, "message": "Phone number not verified", "conversation_id": null, "callSid": null}
                """)));

        assertThatExceptionOfType(ExecutionException.class).isThrownBy(this::call)
                .withRootCauseInstanceOf(IllegalStateException.class)
                .withMessageContaining("ElevenLabs refused call: Phone number not verified");
    }

    @Test
    void httpErrorFailsTheFuture() {
        elevenLabs.stubFor(post(PATH).willReturn(aResponse().withStatus(401)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"detail\":{\"status\":\"invalid_api_key\"}}")));

        assertThatExceptionOfType(ExecutionException.class).isThrownBy(this::call);
    }

    @Test
    void slowResponseTimesOut() {
        elevenLabs.stubFor(post(PATH).willReturn(okJson("{\"success\":true}").withFixedDelay(2_000)));

        assertThatExceptionOfType(ExecutionException.class).isThrownBy(this::call);
    }
}
