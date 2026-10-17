package com.reservationagent.hotelreservationagent.application.port.out;

import com.reservationagent.hotelreservationagent.domain.notification.EmailDelivery;

import java.util.Optional;
import java.util.UUID;

public interface EmailDeliveryRepository {

    EmailDelivery save(EmailDelivery delivery);

    Optional<EmailDelivery> findByProviderMessageId(String providerMessageId);

    /** The most recent delivery attempt for a task's report. */
    Optional<EmailDelivery> findLatestByTask(UUID taskId);
}
