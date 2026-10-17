package com.reservationagent.hotelreservationagent.application.port.in;

import com.reservationagent.hotelreservationagent.domain.hotel.Hotel;
import com.reservationagent.hotelreservationagent.domain.offer.HotelOffer;
import com.reservationagent.hotelreservationagent.domain.reservation.Reservation;
import com.reservationagent.hotelreservationagent.domain.task.BookingTask;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface GetBookingTaskUseCase {

    Optional<TaskSnapshot> get(UUID taskId);

    /**
     * @param hotels the hotels referenced by {@code offers}, keyed by hotel ID
     */
    record TaskSnapshot(
            BookingTask task,
            String municipalityName,
            int candidatesScreened,
            int callsPlaced,
            List<HotelOffer> offers,
            HotelOffer bestOffer,
            Reservation reservation,
            Map<UUID, Hotel> hotels
    ) {
        public TaskSnapshot {
            offers = offers == null ? List.of() : List.copyOf(offers);
            hotels = hotels == null ? Map.of() : Map.copyOf(hotels);
        }
    }
}
