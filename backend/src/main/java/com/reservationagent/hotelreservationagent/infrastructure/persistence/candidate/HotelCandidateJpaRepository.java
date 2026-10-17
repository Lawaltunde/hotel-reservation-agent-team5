package com.reservationagent.hotelreservationagent.infrastructure.persistence.candidate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface HotelCandidateJpaRepository extends JpaRepository<HotelCandidateEntity, UUID> {

    List<HotelCandidateEntity> findByTaskIdAndExclusionReasonIsNullOrderByCallPriorityAsc(UUID taskId);

    int countByTaskId(UUID taskId);
}
