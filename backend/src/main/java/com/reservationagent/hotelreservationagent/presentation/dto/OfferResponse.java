package com.reservationagent.hotelreservationagent.presentation.dto;

import com.reservationagent.hotelreservationagent.domain.hotel.Hotel;
import com.reservationagent.hotelreservationagent.domain.offer.EvidenceStatus;
import com.reservationagent.hotelreservationagent.domain.offer.HotelOffer;
import com.reservationagent.hotelreservationagent.domain.offer.PriceBreakdown;
import com.reservationagent.hotelreservationagent.domain.offer.TaxInclusion;
import com.reservationagent.hotelreservationagent.domain.shared.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One phone quote. Money values are CAD with two decimals; {@code null} means unknown, never zero.
 * {@code negotiatedTotal} is the all-in comparable total and is {@code null} when any mandatory
 * component is unknown. {@code enquiryId} is a hotel reference, never a booking confirmation.
 */
public record OfferResponse(
        UUID offerId,
        UUID hotelId,
        String hotelName,
        String hotelAddress,
        String roomType,
        BigDecimal initialQuotedTotal,
        BigDecimal negotiatedTotal,
        String currency,
        BigDecimal hst,
        BigDecimal municipalAccommodationTax,
        BigDecimal mandatoryFees,
        TaxInclusion taxInclusion,
        String cancellationTerms,
        String depositTerms,
        String enquiryId,
        Instant quoteValidUntil,
        EvidenceStatus evidenceStatus
) {

    public static OfferResponse from(HotelOffer offer, Hotel hotel) {
        PriceBreakdown p = offer.negotiated();
        return new OfferResponse(
                offer.id(),
                offer.hotelId(),
                hotel == null ? null : hotel.name(),
                hotel == null ? null : hotel.address(),
                offer.roomType(),
                amount(offer.initialQuotedTotal()),
                p.comparableTotal().map(Money::amount).orElse(null),
                Money.CURRENCY,
                amount(p.hst()),
                amount(p.municipalAccommodationTax()),
                amount(p.mandatoryFees()),
                p.taxInclusion(),
                offer.cancellationTerms(),
                offer.depositTerms(),
                offer.enquiryId(),
                offer.quoteValidUntil(),
                offer.evidenceStatus());
    }

    private static BigDecimal amount(Money money) {
        return money == null ? null : money.amount();
    }
}
