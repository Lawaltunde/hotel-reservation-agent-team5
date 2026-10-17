package com.reservationagent.hotelreservationagent.application.port.out;

import com.reservationagent.hotelreservationagent.domain.task.BookingTask;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingTaskRepository {

    BookingTask save(BookingTask task);

    Optional<BookingTask> findById(UUID id);

    Optional<BookingTask> findByRequestId(String requestId);

    List<BookingTask> findActive();
}
