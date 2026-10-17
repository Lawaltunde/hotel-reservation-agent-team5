package com.reservationagent.hotelreservationagent.presentation.dto;

import com.reservationagent.hotelreservationagent.domain.hotel.Hotel;
import com.reservationagent.hotelreservationagent.domain.offer.EvidenceStatus;
import com.reservationagent.hotelreservationagent.domain.offer.HotelOffer;
import com.reservationagent.hotelreservationagent.domain.offer.OfferPrice;
import com.reservationagent.hotelreservationagent.domain.offer.PriceBreakdown;
import com.reservationagent.hotelreservationagent.domain.offer.TaxInclusion;
import com.reservationagent.hotelreservationagent.domain.shared.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One phone quote. Money values are CAD with two decimals; {@code null} means unknown, never zero.
 * {@code negotiatedTotal} is the all-in comparable total: the sum of the room subtotal, HST, MAT and
 * mandatory fees when all are known, otherwise the hotel's stated {@code allInTotal} if it includes
 * taxes, otherwise {@code null}. {@code totalsDisagree} is true when that sum and the stated
 * all-in total differ by more than CA$1. {@code enquiryId} is a hotel reference, never a booking
 * confirmation. {@code price} is the ranking price: {@code negotiatedTotal} when known, else (with
 * {@code estimated=true}, {@code priceBasis=ESTIMATED_HST}) a pre-tax charge plus HST with the municipal
 * accommodation tax not confirmed; never present it as a stated total. {@code quoteValidUntil} is computed from the call end and the validity period the
 * hotel stated ({@code null} if none); {@code quoteValidityText} is the hotel's words, verbatim.
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
        BigDecimal roomSubtotal,
        BigDecimal hst,
        BigDecimal municipalAccommodationTax,
        BigDecimal mandatoryFees,
        BigDecimal optionalFees,
        BigDecimal allInTotal,
        TaxInclusion taxInclusion,
        boolean totalsDisagree,
        BigDecimal price,
        boolean estimated,
        OfferPrice.PriceBasis priceBasis,
        String cancellationTerms,
        String depositTerms,
        String enquiryId,
        Instant quoteValidUntil,
        String quoteValidityText,
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
                amount(p.roomSubtotal()),
                amount(p.hst()),
                amount(p.municipalAccommodationTax()),
                amount(p.mandatoryFees()),
                amount(p.optionalFees()),
                amount(p.allInTotal()),
                p.taxInclusion(),
                p.totalsDisagree(),
                p.price().map(x -> x.amount().amount()).orElse(null),
                p.price().map(OfferPrice::estimated).orElse(false),
                p.price().map(OfferPrice::basis).orElse(null),
                offer.cancellationTerms(),
                offer.depositTerms(),
                offer.enquiryId(),
                offer.quoteValidUntil(),
                offer.quoteValidity().statedText(),
                offer.evidenceStatus());
    }

    private static BigDecimal amount(Money money) {
        return money == null ? null : money.amount();
    }
}
