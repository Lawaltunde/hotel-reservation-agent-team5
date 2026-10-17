package com.reservationagent.hotelreservationagent.application.port.out;

import com.reservationagent.hotelreservationagent.domain.webhook.WebhookProvider;

import java.util.Map;

public interface WebhookSignatureVerifier {

    boolean supports(WebhookProvider provider);

    boolean verify(Map<String, String> headers, String rawBody);
}
