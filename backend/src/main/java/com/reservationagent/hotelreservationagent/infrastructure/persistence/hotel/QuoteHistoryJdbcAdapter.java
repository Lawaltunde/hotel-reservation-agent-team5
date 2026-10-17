package com.reservationagent.hotelreservationagent.infrastructure.persistence.hotel;

import com.reservationagent.hotelreservationagent.application.port.out.QuoteHistoryRepository;
import com.reservationagent.hotelreservationagent.domain.shared.Money;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
class QuoteHistoryJdbcAdapter implements QuoteHistoryRepository {

    private static final String MEDIAN = """
            SELECT hotel_id, percentile_cont(0.5) WITHIN GROUP (ORDER BY nightly_total) AS median
            FROM quote_history WHERE hotel_id IN (:ids) GROUP BY hotel_id
            """;

    private final JdbcClient jdbc;

    QuoteHistoryJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<UUID, Money> medianNightlyTotals(Set<UUID> hotelIds) {
        Map<UUID, Money> result = new HashMap<>();
        if (hotelIds.isEmpty()) {
            return result;
        }
        jdbc.sql(MEDIAN).param("ids", hotelIds).query(rs -> {
            BigDecimal median = rs.getBigDecimal("median");
            if (median != null) {
                result.put(rs.getObject("hotel_id", UUID.class), Money.cad(median));
            }
        });
        return result;
    }

    @Override
    public Optional<Money> medianNightlyTotal(UUID hotelId) {
        return Optional.ofNullable(medianNightlyTotals(Set.of(hotelId)).get(hotelId));
    }
}
