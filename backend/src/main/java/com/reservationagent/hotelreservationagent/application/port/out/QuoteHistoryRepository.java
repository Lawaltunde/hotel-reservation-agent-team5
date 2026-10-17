package com.reservationagent.hotelreservationagent.application.port.out;

import com.reservationagent.hotelreservationagent.domain.shared.Money;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Our own past phone quotes: the best price prior for Ontario properties. */
public interface QuoteHistoryRepository {

    /** Median comparable nightly total per hotel, for those with history. */
    Map<UUID, Money> medianNightlyTotals(Set<UUID> hotelIds);

    Optional<Money> medianNightlyTotal(UUID hotelId);
}
