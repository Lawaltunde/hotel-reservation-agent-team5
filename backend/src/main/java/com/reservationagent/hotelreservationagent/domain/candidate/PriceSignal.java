package com.reservationagent.hotelreservationagent.domain.candidate;

import com.reservationagent.hotelreservationagent.domain.hotel.DataSource;
import com.reservationagent.hotelreservationagent.domain.offer.TaxInclusion;
import com.reservationagent.hotelreservationagent.domain.shared.Money;

import java.time.Instant;

/** A price hint used only for ranking. Never treated as a live quote. */
public record PriceSignal(Money estimatedStayTotal, TaxInclusion taxInclusion, DataSource source, Instant observedAt) {
}
