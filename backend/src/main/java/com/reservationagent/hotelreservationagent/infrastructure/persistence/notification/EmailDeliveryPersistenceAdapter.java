package com.reservationagent.hotelreservationagent.infrastructure.persistence.notification;

import com.reservationagent.hotelreservationagent.application.port.out.EmailDeliveryRepository;
import com.reservationagent.hotelreservationagent.domain.notification.EmailDelivery;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
class EmailDeliveryPersistenceAdapter implements EmailDeliveryRepository {

    private final EmailDeliveryJpaRepository jpa;

    EmailDeliveryPersistenceAdapter(EmailDeliveryJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public EmailDelivery save(EmailDelivery d) {
        EmailDeliveryEntity e = jpa.findById(d.id()).orElseGet(EmailDeliveryEntity::new);
        e.id = d.id();
        e.taskId = d.taskId();
        e.recipient = d.recipient();
        e.providerMessageId = d.providerMessageId();
        e.status = d.status();
        e.sentAt = d.sentAt();
        e.deliveredAt = d.deliveredAt();
        e.bouncedAt = d.bouncedAt();
        jpa.save(e);
        return d;
    }

    @Override
    public Optional<EmailDelivery> findByProviderMessageId(String providerMessageId) {
        return jpa.findByProviderMessageId(providerMessageId).map(EmailDeliveryPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<EmailDelivery> findLatestByTask(UUID taskId) {
        return jpa.findFirstByTaskIdOrderBySentAtDesc(taskId).map(EmailDeliveryPersistenceAdapter::toDomain);
    }

    private static EmailDelivery toDomain(EmailDeliveryEntity e) {
        return new EmailDelivery(e.id, e.taskId, e.recipient, e.providerMessageId, e.status, e.sentAt, e.deliveredAt,
                e.bouncedAt);
    }
}
