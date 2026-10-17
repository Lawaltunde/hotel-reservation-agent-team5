package com.reservationagent.hotelreservationagent.infrastructure.persistence.hotel;

import com.reservationagent.hotelreservationagent.application.port.out.HotelIndex;
import com.reservationagent.hotelreservationagent.domain.geography.GeoPoint;
import com.reservationagent.hotelreservationagent.domain.geography.Municipality;
import com.reservationagent.hotelreservationagent.domain.hotel.Hotel;
import com.reservationagent.hotelreservationagent.domain.hotel.PhoneNumber;
import com.reservationagent.hotelreservationagent.domain.hotel.PriceTier;
import com.reservationagent.hotelreservationagent.domain.hotel.PropertyType;
import com.reservationagent.hotelreservationagent.domain.shared.DomainException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Spatial queries against the open-data hotel index. Typically answers in milliseconds. */
@Repository
class HotelIndexJdbcAdapter implements HotelIndex {

    private static final String COLUMNS = """
            h.id, h.name, h.address, h.municipality_id, ST_Y(h.location::geometry) AS lat, ST_X(h.location::geometry) AS lon,
            h.property_type, h.stars, h.brand, h.price_tier, h.phones, h.phone_confidence, h.website, h.operational, h.last_seen_at
            """;

    private final JdbcClient jdbc;

    HotelIndexJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<NearbyHotel> findNear(Municipality municipality, double radiusKm, Set<PropertyType> types, int limit) {
        return jdbc.sql("SELECT " + COLUMNS + """
                        , ST_Distance(h.location, m.centre) / 1000.0 AS distance_km
                        FROM hotels h JOIN municipalities m ON m.id = :municipalityId
                        WHERE h.property_type IN (:types)
                          AND (h.municipality_id = m.id OR ST_DWithin(h.location, m.centre, :radiusMetres))
                        ORDER BY distance_km
                        LIMIT :limit
                        """)
                .param("municipalityId", municipality.id())
                .param("types", types.stream().map(Enum::name).toList())
                .param("radiusMetres", radiusKm * 1000)
                .param("limit", limit)
                .query((rs, i) -> new NearbyHotel(map(rs), rs.getDouble("distance_km")))
                .list();
    }

    @Override
    public Optional<Hotel> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM hotels h WHERE h.id = :id")
                .param("id", id)
                .query((rs, i) -> map(rs))
                .optional();
    }

    private static Hotel map(ResultSet rs) throws SQLException {
        String[] phones = rs.getArray("phones") == null ? new String[0] : (String[]) rs.getArray("phones").getArray();
        return new Hotel(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("address"),
                rs.getObject("municipality_id", UUID.class),
                new GeoPoint(rs.getDouble("lat"), rs.getDouble("lon")),
                PropertyType.valueOf(rs.getString("property_type")),
                (Integer) rs.getObject("stars"),
                rs.getString("brand"),
                PriceTier.valueOf(rs.getString("price_tier")),
                Arrays.stream(phones).map(HotelIndexJdbcAdapter::phoneOrNull).filter(Objects::nonNull).toList(),
                rs.getDouble("phone_confidence"),
                rs.getString("website"),
                rs.getBoolean("operational"),
                rs.getTimestamp("last_seen_at").toInstant());
    }

    private static PhoneNumber phoneOrNull(String e164) {
        try {
            return new PhoneNumber(e164);
        } catch (DomainException e) {
            return null;
        }
    }
}
