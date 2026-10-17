package com.reservationagent.hotelreservationagent.application.port.out;

import com.reservationagent.hotelreservationagent.domain.notification.EmailDelivery;

import java.util.Optional;

public interface EmailDeliveryRepository {

    EmailDelivery save(EmailDelivery delivery);

    Optional<EmailDelivery> findByProviderMessageId(String providerMessageId);
}
