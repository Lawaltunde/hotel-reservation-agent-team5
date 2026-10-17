package com.reservationagent.hotelreservationagent.infrastructure.persistence.task;

import com.reservationagent.hotelreservationagent.domain.task.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface BookingTaskJpaRepository extends JpaRepository<BookingTaskEntity, UUID> {

    Optional<BookingTaskEntity> findByRequestId(String requestId);

    List<BookingTaskEntity> findByStatusNotIn(Collection<TaskStatus> statuses);
}
