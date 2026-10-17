package com.reservationagent.hotelreservationagent.unit.presentation;

import com.reservationagent.hotelreservationagent.presentation.rest.HealthController;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/** The API health check is only UP when the database answers. */
class HealthControllerTest {

    @Test
    void upWhenTheDatabaseAnswers() {
        var response = new HealthController("svc", () -> true).health();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo("UP");
        assertThat(response.getBody().database()).isEqualTo("UP");
    }

    @Test
    void downWith503WhenTheDatabaseDoesNot() {
        var response = new HealthController("svc", () -> false).health();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().status()).isEqualTo("DOWN");
        assertThat(response.getBody().database()).isEqualTo("DOWN");
    }
}
