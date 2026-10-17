package com.reservationagent.hotelreservationagent.infrastructure.persistence.notification;

import com.reservationagent.hotelreservationagent.domain.notification.EmailStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_deliveries")
class EmailDeliveryEntity {

    @Id
    UUID id;
    UUID taskId;
    String recipient;
    String providerMessageId;
    @Enumerated(EnumType.STRING)
    EmailStatus status;
    Instant sentAt;
    Instant deliveredAt;
    Instant bouncedAt;

    protected EmailDeliveryEntity() {
    }
}
