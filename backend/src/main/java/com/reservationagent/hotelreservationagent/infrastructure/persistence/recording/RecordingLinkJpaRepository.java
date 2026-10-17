package com.reservationagent.hotelreservationagent.infrastructure.persistence.recording;

import org.springframework.data.jpa.repository.JpaRepository;

interface RecordingLinkJpaRepository extends JpaRepository<RecordingLinkEntity, String> {
}
