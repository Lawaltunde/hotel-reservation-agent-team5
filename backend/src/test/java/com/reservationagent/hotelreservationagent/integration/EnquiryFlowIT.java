package com.reservationagent.hotelreservationagent.integration;

import com.reservationagent.hotelreservationagent.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The phase 2 API over real HTTP and a real database. The hotel index is empty, so an Ontario
 * enquiry ends {@code COMPLETED} with outcome {@code NO_ELIGIBLE_HOTELS} (the email goes to the
 * logging stub).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class EnquiryFlowIT {

    @Autowired
    private RestTestClient client;

    private static String body(String city, String requestId) {
        LocalDate checkIn = LocalDate.now(ZoneId.of("America/Toronto")).plusDays(30);
        return """
                {"requestId": "%s", "city": "%s", "checkIn": "%s", "checkOut": "%s", "rooms": 1, "adults": 2,
                 "children": 0, "destinationEmail": "it@example.com", "deadline": "%s",
                 "authorizationMode": "ENQUIRY_ONLY"}
                """.formatted(requestId, city, checkIn, checkIn.plusDays(2), Instant.now().plus(Duration.ofHours(2)));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getTask(String taskId) {
        return client.get().uri("/api/tasks/{id}", taskId).exchange()
                .expectStatus().isOk()
                .expectBody(Map.class).returnResult().getResponseBody();
    }

    @Test
    @SuppressWarnings("unchecked")
    void ontarioEnquiryWithAnEmptyIndexCompletesWithNoEligibleHotels() throws Exception {
        String requestId = UUID.randomUUID().toString();
        Map<String, Object> accepted = client.post().uri("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                .body(body("Ottawa", requestId))
                .exchange()
                .expectStatus().isAccepted()
                .expectHeader().valueMatches("Location", "/api/tasks/[0-9a-f-]{36}")
                .expectBody(Map.class).returnResult().getResponseBody();
        String taskId = (String) accepted.get("taskId");
        assertThat(taskId).isNotBlank();

        Map<String, Object> task = getTask(taskId);
        Instant giveUp = Instant.now().plusSeconds(20);
        while (!"COMPLETED".equals(task.get("status")) && Instant.now().isBefore(giveUp)) {
            Thread.sleep(200);
            task = getTask(taskId);
        }

        assertThat(task).containsEntry("status", "COMPLETED")
                .containsEntry("outcome", "NO_ELIGIBLE_HOTELS")
                .containsEntry("city", "Ottawa")
                .containsEntry("callsPlaced", 0)
                .containsEntry("bestOffer", null);
        assertThat((java.util.List<Object>) task.get("offers")).isEmpty();

        // Resubmitting the same requestId returns the same task instead of starting another.
        Map<String, Object> again = client.post().uri("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                .body(body("Ottawa", requestId))
                .exchange()
                .expectStatus().isAccepted()
                .expectBody(Map.class).returnResult().getResponseBody();
        assertThat(again).containsEntry("taskId", taskId).containsEntry("status", "COMPLETED");
    }

    @Test
    void cityOutsideOntarioIsA422Problem() {
        client.post().uri("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                .body(body("Vancouver", UUID.randomUUID().toString()))
                .exchange()
                .expectStatus().isEqualTo(422)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(422)
                .jsonPath("$.title").isEqualTo("Location out of scope")
                .jsonPath("$.detail").isEqualTo("'Vancouver' is not a supported Ontario municipality")
                .jsonPath("$.suggestions").isArray()
                .jsonPath("$.suggestions.length()").isEqualTo(0);
    }

    @Test
    void cityLookupIsCaseInsensitiveAndAliasesWork() {
        client.post().uri("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                .body(body("sudbury", UUID.randomUUID().toString()))
                .exchange()
                .expectStatus().isAccepted();
        client.post().uri("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                .body(body("OTTAWA", UUID.randomUUID().toString()))
                .exchange()
                .expectStatus().isAccepted();
    }

    @Test
    void pastCheckInIsA400ProblemWithErrors() {
        String past = body("Ottawa", UUID.randomUUID().toString())
                .replaceFirst("\"checkIn\": \"[0-9-]+\"", "\"checkIn\": \"2020-01-01\"");
        client.post().uri("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                .body(past)
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.errors[0]").isEqualTo("checkIn is in the past");
    }

    @Test
    void municipalityAutocompleteServesTheSeed() {
        client.get().uri("/api/municipalities?q=ott").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].name").isEqualTo("Ottawa")
                .jsonPath("$[0].timeZone").isEqualTo("America/Toronto");
        client.get().uri("/api/municipalities?q=ken").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].name").isEqualTo("Kenora")
                .jsonPath("$[0].timeZone").isEqualTo("America/Winnipeg");
        client.get().uri("/api/municipalities?q=vancouver").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(0);
    }

    @Test
    void unsignedWebhooksAreRejected() {
        client.post().uri("/webhooks/elevenlabs").contentType(MediaType.APPLICATION_JSON).body("{}")
                .exchange().expectStatus().isUnauthorized();
        client.post().uri("/webhooks/resend").contentType(MediaType.APPLICATION_JSON).body("{}")
                .exchange().expectStatus().isUnauthorized();
    }
}
