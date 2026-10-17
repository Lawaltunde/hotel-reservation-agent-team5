package com.reservationagent.hotelreservationagent.infrastructure.client.resend;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.reservationagent.hotelreservationagent.application.port.out.EmailProvider.Attachment;
import com.reservationagent.hotelreservationagent.application.port.out.EmailProvider.Email;
import com.reservationagent.hotelreservationagent.infrastructure.config.HotelAgentProperties;
import com.reservationagent.hotelreservationagent.infrastructure.config.TestWebClients;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
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
class ResendEmailProviderTest {

    @RegisterExtension
    static WireMockExtension resend = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private ResendEmailProvider provider;

    @BeforeEach
    void setUp() {
        HotelAgentProperties p = TestWebClients.properties(resend.baseUrl(), Duration.ofMillis(500));
        provider = new ResendEmailProvider(TestWebClients.resend(p), p);
    }

    private String send(Email email) throws Exception {
        return provider.send(email).get(5, TimeUnit.SECONDS);
    }

    @Test
    void sendsThePlainTextReportWithBase64Attachments() throws Exception {
        resend.stubFor(post("/emails").willReturn(okJson("{\"id\":\"49a3999c-0ce1-4ea6-ab68-afcd6dc2e794\"}")));
        byte[] transcript = "Agent: Hello\nHotel: Hi — bonjour".getBytes(StandardCharsets.UTF_8);

        String id = send(new Email("traveller@example.com", "Hotel negotiation result — Ottawa, ON", "Hello,\n\nResult…",
                List.of(new Attachment("transcript.txt", "text/plain", transcript))));

        assertThat(id).isEqualTo("49a3999c-0ce1-4ea6-ab68-afcd6dc2e794");
        resend.verify(postRequestedFor(urlEqualTo("/emails"))
                .withHeader("Authorization", equalTo("Bearer re_test_key"))
                .withRequestBody(equalToJson("""
                        {"from": "Hotel Deal Agent <agent@example.com>", "to": ["traveller@example.com"],
                         "subject": "Hotel negotiation result — Ottawa, ON", "text": "Hello,\\n\\nResult…",
                         "attachments": [{"filename": "transcript.txt", "content": "%s"}]}
                        """.formatted(Base64.getEncoder().encodeToString(transcript)))));
    }

    @Test
    void noAttachmentsSendsAnEmptyList() throws Exception {
        resend.stubFor(post("/emails").willReturn(okJson("{\"id\":\"em_1\"}")));

        assertThat(send(new Email("a@example.com", "s", "t", List.of()))).isEqualTo("em_1");
        resend.verify(postRequestedFor(urlEqualTo("/emails")).withRequestBody(equalToJson(
                "{\"from\":\"Hotel Deal Agent <agent@example.com>\",\"to\":[\"a@example.com\"],\"subject\":\"s\",\"text\":\"t\",\"attachments\":[]}")));
    }

    @Test
    void validationErrorFailsTheFuture() {
        resend.stubFor(post("/emails").willReturn(aResponse().withStatus(422)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"statusCode\":422,\"name\":\"validation_error\",\"message\":\"Invalid `from` field.\"}")));

        assertThatExceptionOfType(ExecutionException.class)
                .isThrownBy(() -> send(new Email("a@example.com", "s", "t", List.of())));
    }

    @Test
    void slowResponseTimesOut() {
        resend.stubFor(post("/emails").willReturn(okJson("{\"id\":\"late\"}").withFixedDelay(2_000)));

        assertThatExceptionOfType(ExecutionException.class)
                .isThrownBy(() -> send(new Email("a@example.com", "s", "t", List.of())));
    }
}
