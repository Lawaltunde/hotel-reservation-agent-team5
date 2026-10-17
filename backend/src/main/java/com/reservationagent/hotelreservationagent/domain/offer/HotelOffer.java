package com.reservationagent.hotelreservationagent.domain.offer;

import com.reservationagent.hotelreservationagent.domain.shared.Money;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A quote obtained by phone. {@code enquiryId} is a hotel reference for the enquiry and is
 * never a reservation confirmation number.
 */
public record HotelOffer(
        UUID id,
        UUID taskId,
        UUID hotelId,
        UUID callId,
        boolean availabilityConfirmed,
        LocalDate checkIn,
        LocalDate checkOut,
        String roomType,
        int guestCount,
        Money initialQuotedTotal,
        PriceBreakdown negotiated,
        String cancellationTerms,
        String depositTerms,
        String enquiryId,
        Instant quoteValidUntil,
        boolean holdsRoom,
        EvidenceStatus evidenceStatus,
        Instant receivedAt
) {

    public Money savings() {
        return negotiated.comparableTotal()
                .filter(total -> initialQuotedTotal != null)
                .map(initialQuotedTotal::minus)
                .orElse(null);
    }
}
