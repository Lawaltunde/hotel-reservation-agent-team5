package com.reservationagent.hotelreservationagent.application.port.in;

import com.reservationagent.hotelreservationagent.application.service.notification.SentReport;
import com.reservationagent.hotelreservationagent.domain.hotel.Hotel;
import com.reservationagent.hotelreservationagent.domain.notification.EmailDelivery;
import com.reservationagent.hotelreservationagent.domain.offer.HotelOffer;
import com.reservationagent.hotelreservationagent.domain.reservation.Reservation;
import com.reservationagent.hotelreservationagent.domain.task.BookingTask;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface GetBookingTaskUseCase {

    Optional<TaskSnapshot> get(UUID taskId);

    /**
     * @param hotels   the hotels referenced by {@code offers}, keyed by hotel ID
     * @param report   the report email as sent, or {@code null} before it is composed
     * @param delivery the latest delivery attempt of the report, or {@code null}
     * @param salesTaxRate the destination province's sales tax on accommodation, for estimated prices
     */
    record TaskSnapshot(
            BookingTask task,
            String municipalityName,
            int candidatesScreened,
            int callsPlaced,
            List<HotelOffer> offers,
            HotelOffer bestOffer,
            Reservation reservation,
            Map<UUID, Hotel> hotels,
            SentReport report,
            EmailDelivery delivery,
            BigDecimal salesTaxRate
    ) {
        public TaskSnapshot(BookingTask task, String municipalityName, int candidatesScreened, int callsPlaced,
                            List<HotelOffer> offers, HotelOffer bestOffer, Reservation reservation, Map<UUID, Hotel> hotels,
                            SentReport report, EmailDelivery delivery) {
            this(task, municipalityName, candidatesScreened, callsPlaced, offers, bestOffer, reservation, hotels, report,
                    delivery, null);
        }

        public TaskSnapshot {
            salesTaxRate = salesTaxRate == null ? com.reservationagent.hotelreservationagent.domain.offer.PriceBreakdown.ONTARIO_HST_RATE : salesTaxRate;
            offers = offers == null ? List.of() : List.copyOf(offers);
            hotels = hotels == null ? Map.of() : Map.copyOf(hotels);
        }
    }
}
