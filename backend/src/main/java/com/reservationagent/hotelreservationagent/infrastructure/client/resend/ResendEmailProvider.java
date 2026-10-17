package com.reservationagent.hotelreservationagent.infrastructure.client.resend;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.reservationagent.hotelreservationagent.application.port.out.EmailProvider;
import com.reservationagent.hotelreservationagent.infrastructure.config.HotelAgentProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Sends the report through the Resend API. A returned ID means accepted, not delivered. */
@Component
@ConditionalOnProperty(name = "hotel-agent.resend.enabled", havingValue = "true")
class ResendEmailProvider implements EmailProvider {

    private final WebClient client;
    private final String from;

    ResendEmailProvider(@Qualifier("resend") WebClient client, HotelAgentProperties properties) {
        this.client = client;
        this.from = properties.resend().from();
    }

    @Override
    public CompletableFuture<String> send(Email email) {
        SendRequest request = new SendRequest(from, List.of(email.to()), email.subject(), email.textBody(),
                email.attachments().stream()
                        .map(a -> new SendAttachment(a.filename(), Base64.getEncoder().encodeToString(a.content())))
                        .toList());
        return client.post()
                .uri("/emails")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(SendResponse.class)
                .map(SendResponse::id)
                .toFuture();
    }

    record SendRequest(String from, List<String> to, String subject, String text, List<SendAttachment> attachments) {
    }

    record SendAttachment(String filename, String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SendResponse(String id) {
    }
}
