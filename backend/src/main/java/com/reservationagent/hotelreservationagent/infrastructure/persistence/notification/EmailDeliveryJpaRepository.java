package com.reservationagent.hotelreservationagent.infrastructure.persistence.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface EmailDeliveryJpaRepository extends JpaRepository<EmailDeliveryEntity, UUID> {

    Optional<EmailDeliveryEntity> findByProviderMessageId(String providerMessageId);
}
