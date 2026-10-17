package com.reservationagent.hotelreservationagent.infrastructure.persistence.offer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface HotelOfferJpaRepository extends JpaRepository<HotelOfferEntity, UUID> {

    List<HotelOfferEntity> findByTaskIdOrderByReceivedAtAsc(UUID taskId);
}
