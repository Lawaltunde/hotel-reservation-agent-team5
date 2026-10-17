package com.reservationagent.hotelreservationagent.application.port.out;

import com.reservationagent.hotelreservationagent.domain.reservation.Reservation;

import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository {

    /**
     * Inserts the single reservation row for a task. Must fail if one already exists
     * (unique constraint on task_id); this is the last line of defence against double booking.
     *
     * @return false if a reservation already existed
     */
    boolean claim(Reservation reservation);

    Reservation save(Reservation reservation);

    Optional<Reservation> findByTask(UUID taskId);
}
