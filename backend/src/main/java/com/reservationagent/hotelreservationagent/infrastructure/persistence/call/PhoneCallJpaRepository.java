package com.reservationagent.hotelreservationagent.infrastructure.persistence.call;

import com.reservationagent.hotelreservationagent.domain.call.CallStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface PhoneCallJpaRepository extends JpaRepository<PhoneCallEntity, UUID> {

    Optional<PhoneCallEntity> findByConversationId(String conversationId);

    List<PhoneCallEntity> findByTaskId(UUID taskId);

    int countByTaskIdAndStatusIn(UUID taskId, Collection<CallStatus> statuses);
}
