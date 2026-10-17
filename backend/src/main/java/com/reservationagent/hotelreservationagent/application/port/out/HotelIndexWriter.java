package com.reservationagent.hotelreservationagent.application.port.out;

import com.reservationagent.hotelreservationagent.application.port.out.HotelDiscoverySource.DiscoveredHotel;

import java.util.List;
import java.util.UUID;

/** Adds or refreshes hotels found at task time. */
public interface HotelIndexWriter {

    /** Inserts new hotels and refreshes known ones (matched on source key); returns how many were written. */
    int upsert(List<DiscoveredHotel> hotels, UUID municipalityId);
}
