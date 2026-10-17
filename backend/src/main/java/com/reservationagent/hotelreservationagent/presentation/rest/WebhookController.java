package com.reservationagent.hotelreservationagent.presentation.rest;

import com.reservationagent.hotelreservationagent.application.port.in.HandleWebhookUseCase;
import com.reservationagent.hotelreservationagent.application.port.in.HandleWebhookUseCase.Result;
import com.reservationagent.hotelreservationagent.domain.webhook.WebhookProvider;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Provider callbacks. The raw body is passed through untouched because signatures are computed
 * over the exact bytes; header names are lower-cased for the verifiers.
 */
@RestController
@RequestMapping("/webhooks")
public class WebhookController {

    private final HandleWebhookUseCase webhooks;

    public WebhookController(HandleWebhookUseCase webhooks) {
        this.webhooks = webhooks;
    }

    @PostMapping("/elevenlabs")
    public ResponseEntity<Void> elevenLabs(@RequestHeader HttpHeaders headers,
                                           @RequestBody(required = false) String body) {
        return handle(WebhookProvider.ELEVENLABS, headers, body);
    }

    @PostMapping("/resend")
    public ResponseEntity<Void> resend(@RequestHeader HttpHeaders headers,
                                       @RequestBody(required = false) String body) {
        return handle(WebhookProvider.RESEND, headers, body);
    }

    private ResponseEntity<Void> handle(WebhookProvider provider, HttpHeaders headers, String body) {
        Result result = webhooks.handle(provider, lowerCase(headers), body == null ? "" : body);
        return switch (result) {
            case ACCEPTED, DUPLICATE -> ResponseEntity.ok().build();
            case REJECTED_SIGNATURE -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        };
    }

    private static Map<String, String> lowerCase(HttpHeaders headers) {
        Map<String, String> result = new HashMap<>();
        headers.forEach((name, values) -> {
            if (!values.isEmpty()) {
                result.put(name.toLowerCase(Locale.ROOT), values.getFirst());
            }
        });
        return result;
    }
}
