package com.reservationagent.hotelreservationagent.infrastructure.persistence.webhook;

import com.reservationagent.hotelreservationagent.application.port.out.WebhookEventRepository;
import com.reservationagent.hotelreservationagent.domain.webhook.WebhookEvent;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

/** Idempotency is enforced by UNIQUE(provider, external_event_id); provider retries become no-ops. */
@Repository
class WebhookEventPersistenceAdapter implements WebhookEventRepository {

    private final JdbcClient jdbc;

    WebhookEventPersistenceAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean insertIfAbsent(WebhookEvent event) {
        return jdbc.sql("""
                        INSERT INTO webhook_events (id, provider, external_event_id, event_type, payload, received_at, status)
                        VALUES (:id, :provider, :externalId, :type, :payload, :receivedAt, :status)
                        ON CONFLICT (provider, external_event_id) DO NOTHING
                        """)
                .param("id", event.id())
                .param("provider", event.provider().name())
                .param("externalId", event.externalEventId())
                .param("type", event.eventType())
                .param("payload", event.payload())
                .param("receivedAt", Timestamp.from(event.receivedAt()))
                .param("status", event.status().name())
                .update() == 1;
    }

    @Override
    public void markProcessed(WebhookEvent event, WebhookEvent.ProcessingStatus status) {
        jdbc.sql("UPDATE webhook_events SET status = :status, processed_at = :at WHERE id = :id")
                .param("status", status.name())
                .param("at", Timestamp.from(Instant.now()))
                .param("id", event.id())
                .update();
    }
}
