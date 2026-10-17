package com.reservationagent.hotelreservationagent.infrastructure.persistence.offer;

import com.reservationagent.hotelreservationagent.domain.offer.EvidenceStatus;
import com.reservationagent.hotelreservationagent.domain.offer.TaxInclusion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "hotel_offers")
class HotelOfferEntity {

    @Id
    UUID id;
    UUID taskId;
    UUID hotelId;
    UUID callId;
    boolean availabilityConfirmed;
    LocalDate checkIn;
    LocalDate checkOut;
    String roomType;
    int guestCount;
    BigDecimal initialQuotedTotal;
    BigDecimal roomSubtotal;
    BigDecimal hst;
    @Column(name = "mat")
    BigDecimal municipalAccommodationTax;
    BigDecimal mandatoryFees;
    BigDecimal optionalFees;
    BigDecimal allInTotal;
    @Enumerated(EnumType.STRING)
    TaxInclusion taxInclusion;
    BigDecimal comparableTotal;
    String cancellationTerms;
    String depositTerms;
    String enquiryId;
    Instant quoteValidUntil;
    boolean holdsRoom;
    @Enumerated(EnumType.STRING)
    EvidenceStatus evidenceStatus;
    Instant receivedAt;

    protected HotelOfferEntity() {
    }
}
