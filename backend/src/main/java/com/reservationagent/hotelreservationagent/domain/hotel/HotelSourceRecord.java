package com.reservationagent.hotelreservationagent.domain.hotel;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** A raw record from one data source, kept for provenance and licence attribution. */
public record HotelSourceRecord(
        UUID id,
        UUID hotelId,
        DataSource source,
        String sourceId,
        Map<String, String> tags,
        Instant retrievedAt
) {

    public HotelSourceRecord {
        tags = tags == null ? Map.of() : Map.copyOf(tags);
    }
}
