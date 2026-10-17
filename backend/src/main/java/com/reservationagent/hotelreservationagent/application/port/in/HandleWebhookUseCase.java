package com.reservationagent.hotelreservationagent.application.port.in;

import com.reservationagent.hotelreservationagent.domain.webhook.WebhookProvider;

import java.util.Map;

/** Receives raw provider webhooks; signature checks and idempotency happen behind this port. */
public interface HandleWebhookUseCase {

    Result handle(WebhookProvider provider, Map<String, String> headers, String rawBody);

    enum Result { ACCEPTED, DUPLICATE, REJECTED_SIGNATURE }
}
