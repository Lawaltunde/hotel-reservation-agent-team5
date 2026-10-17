package com.reservationagent.hotelreservationagent.infrastructure.config;

import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * Builds the provider WebClients exactly as {@link InfrastructureConfig} does (base URL, auth
 * header, timeouts), so client tests exercise the production wiring against WireMock.
 */
public final class TestWebClients {

    private static final InfrastructureConfig CONFIG = new InfrastructureConfig();

    private TestWebClients() {
    }

    public static HotelAgentProperties properties(String baseUrl, Duration responseTimeout) {
        return new HotelAgentProperties(
                null,
                new HotelAgentProperties.Http(Duration.ofSeconds(2), responseTimeout),
                new HotelAgentProperties.ElevenLabs(true, baseUrl, "xi-test-key", "agent_test", "phnum_test", "unused"),
                new HotelAgentProperties.GooglePlaces(true, baseUrl, "goog-test-key", 300),
                new HotelAgentProperties.Resend(true, baseUrl, "re_test_key", "Hotel Deal Agent <agent@example.com>", "unused"),
                null);
    }

    public static WebClient googlePlaces(HotelAgentProperties p) {
        return CONFIG.googlePlacesWebClient(WebClient.builder(), CONFIG.reactorClientHttpConnector(p), p);
    }

    public static WebClient elevenLabs(HotelAgentProperties p) {
        return CONFIG.elevenLabsWebClient(WebClient.builder(), CONFIG.reactorClientHttpConnector(p), p);
    }

    public static WebClient resend(HotelAgentProperties p) {
        return CONFIG.resendWebClient(WebClient.builder(), CONFIG.reactorClientHttpConnector(p), p);
    }
}
