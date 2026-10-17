package com.reservationagent.hotelreservationagent.infrastructure.persistence.candidate;

import com.reservationagent.hotelreservationagent.domain.candidate.ExclusionReason;
import com.reservationagent.hotelreservationagent.domain.hotel.DataSource;
import com.reservationagent.hotelreservationagent.domain.offer.TaxInclusion;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "hotel_candidates")
class HotelCandidateEntity {

    @Id
    UUID id;
    UUID taskId;
    UUID hotelId;
    BigDecimal priceTotal;
    @Enumerated(EnumType.STRING)
    TaxInclusion priceTaxInclusion;
    @Enumerated(EnumType.STRING)
    DataSource priceSource;
    Instant priceObservedAt;
    double distanceKm;
    Integer callPriority;
    @Enumerated(EnumType.STRING)
    ExclusionReason exclusionReason;

    protected HotelCandidateEntity() {
    }
}
