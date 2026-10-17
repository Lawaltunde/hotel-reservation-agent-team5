package com.reservationagent.hotelreservationagent.infrastructure.persistence.reservation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface ReservationJpaRepository extends JpaRepository<ReservationEntity, UUID> {

    Optional<ReservationEntity> findByTaskId(UUID taskId);
}
