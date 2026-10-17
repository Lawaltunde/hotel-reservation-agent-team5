package com.reservationagent.hotelreservationagent.application.port.out;

import com.reservationagent.hotelreservationagent.domain.offer.HotelOffer;

import java.util.List;
import java.util.UUID;

public interface OfferRepository {

    HotelOffer save(HotelOffer offer);

    List<HotelOffer> findByTask(UUID taskId);
}
