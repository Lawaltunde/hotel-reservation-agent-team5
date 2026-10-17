package com.reservationagent.hotelreservationagent.infrastructure.persistence.geography;

import com.reservationagent.hotelreservationagent.application.port.out.MunicipalityRepository;
import com.reservationagent.hotelreservationagent.domain.geography.GeoPoint;
import com.reservationagent.hotelreservationagent.domain.geography.Municipality;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.SQLException;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
class MunicipalityJdbcAdapter implements MunicipalityRepository {

    private static final String SELECT = """
            SELECT id, name, csd_code, alias_keys, ST_Y(centre::geometry) AS lat, ST_X(centre::geometry) AS lon,
                   time_zone, mat_rate
            FROM municipalities
            """;

    private static final RowMapper<Municipality> MAPPER = (rs, i) -> new Municipality(
            rs.getObject("id", UUID.class),
            rs.getString("name"),
            rs.getString("csd_code"),
            toSet(rs.getArray("alias_keys")),
            new GeoPoint(rs.getDouble("lat"), rs.getDouble("lon")),
            ZoneId.of(rs.getString("time_zone")),
            rs.getBigDecimal("mat_rate"));

    private final JdbcClient jdbc;

    MunicipalityJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Municipality> findByNameOrAlias(String name) {
        return jdbc.sql(SELECT + " WHERE name_key = lower(unaccent(:n)) OR lower(unaccent(:n)) = ANY(alias_keys)")
                .param("n", name)
                .query(MAPPER)
                .list();
    }

    @Override
    public List<Municipality> searchByPrefix(String prefix, int limit) {
        return jdbc.sql(SELECT + " WHERE name_key LIKE lower(unaccent(:p)) || '%' ORDER BY name LIMIT :limit")
                .param("p", prefix.replace("%", "").replace("_", ""))
                .param("limit", limit)
                .query(MAPPER)
                .list();
    }

    @Override
    public Optional<Municipality> findById(UUID id) {
        return jdbc.sql(SELECT + " WHERE id = :id").param("id", id).query(MAPPER).optional();
    }

    @Override
    public Municipality addGeocoded(String name, GeoPoint centre) {
        List<Municipality> existing = findByNameOrAlias(name);
        if (!existing.isEmpty()) {
            return existing.getFirst();
        }
        // North-western Ontario (Kenora district) is on Central time.
        String zone = centre.longitude() < -90.0 ? "America/Winnipeg" : "America/Toronto";
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO municipalities (id, name, name_key, alias_keys, centre, time_zone)
                        VALUES (:id, :name, lower(unaccent(:name)), '{}', ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography, :zone)
                        """)
                .param("id", id).param("name", name).param("lon", centre.longitude()).param("lat", centre.latitude())
                .param("zone", zone)
                .update();
        return findById(id).orElseThrow();
    }

    private static Set<String> toSet(Array array) throws SQLException {
        return array == null ? Set.of() : Set.of((String[]) array.getArray());
    }
}
