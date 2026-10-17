package com.reservationagent.hotelreservationagent.application.port.out;

import com.reservationagent.hotelreservationagent.domain.call.PhoneCall;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PhoneCallRepository {

    PhoneCall save(PhoneCall call);

    Optional<PhoneCall> findByConversationId(String conversationId);

    List<PhoneCall> findByTask(UUID taskId);

    int countActive(UUID taskId);
}
