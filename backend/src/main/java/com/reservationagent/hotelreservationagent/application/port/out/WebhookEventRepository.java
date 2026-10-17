package com.reservationagent.hotelreservationagent.application.port.out;

import com.reservationagent.hotelreservationagent.domain.webhook.WebhookEvent;

public interface WebhookEventRepository {

    /** @return false if an event with the same provider and external ID was already stored */
    boolean insertIfAbsent(WebhookEvent event);

    void markProcessed(WebhookEvent event, WebhookEvent.ProcessingStatus status);
}
