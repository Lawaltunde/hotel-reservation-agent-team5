package com.reservationagent.hotelreservationagent.integration;

import com.reservationagent.hotelreservationagent.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The single jar serves the Next.js static export at {@code /} next to the API.
 * Requires the frontend module to be built first (root {@code ./mvnw verify} does this).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class FrontendServingIT {

    @Autowired
    private RestTestClient client;

    @Test
    void rootServesTheFrontend() {
        String html = client.get().uri("/")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
        assertThat(html).contains("Hotel Deal Agent");
    }

    @Test
    void apiStillWinsOverStaticFiles() {
        client.get().uri("/api/health")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON);
    }

    @Test
    void unknownPageIsNotFound() {
        client.get().uri("/no-such-page").exchange().expectStatus().isNotFound();
    }
}
