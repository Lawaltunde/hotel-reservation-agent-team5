package com.reservationagent.hotelreservationagent.infrastructure.persistence.hotel;

import com.reservationagent.hotelreservationagent.application.port.out.HotelDiscoverySource.DiscoveredHotel;
import com.reservationagent.hotelreservationagent.application.port.out.HotelIndexWriter;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/** Upserts discovered hotels on {@code canonical_key}; the row ID stays stable across refreshes. */
@Repository
class HotelIndexJdbcWriter implements HotelIndexWriter {

    private final JdbcClient jdbc;
    private final Clock clock;

    HotelIndexJdbcWriter(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    @Transactional
    public int upsert(List<DiscoveredHotel> hotels, UUID municipalityId) {
        int written = 0;
        Timestamp now = Timestamp.from(clock.instant());
        for (DiscoveredHotel h : hotels) {
            if (h.location() == null) continue;
            String[] phones = h.phone() == null ? new String[0] : new String[] {h.phone().e164()};
            written += jdbc.sql("""
                            INSERT INTO hotels (id, canonical_key, name, address, municipality_id, location, property_type,
                                                price_tier, phones, phone_confidence, operational, last_seen_at)
                            VALUES (:id, :key, :name, :address, :municipalityId,
                                    ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography, :type, :tier, :phones,
                                    :confidence, :operational, :now)
                            ON CONFLICT (canonical_key) DO UPDATE SET
                                name = EXCLUDED.name, address = EXCLUDED.address, location = EXCLUDED.location,
                                property_type = EXCLUDED.property_type, price_tier = EXCLUDED.price_tier,
                                phones = EXCLUDED.phones, phone_confidence = EXCLUDED.phone_confidence,
                                operational = EXCLUDED.operational, last_seen_at = EXCLUDED.last_seen_at,
                                municipality_id = COALESCE(hotels.municipality_id, EXCLUDED.municipality_id)
                            """)
                    .param("id", UUID.randomUUID())
                    .param("key", h.sourceKey())
                    .param("name", h.name())
                    .param("address", h.address())
                    .param("municipalityId", municipalityId)
                    .param("lon", h.location().longitude())
                    .param("lat", h.location().latitude())
                    .param("type", h.propertyType().name())
                    .param("tier", h.priceTier().name())
                    .param("phones", phones)
                    .param("confidence", h.phone() == null ? 0.0 : 0.9)
                    .param("operational", h.operational())
                    .param("now", now)
                    .update();
        }
        return written;
    }
}
