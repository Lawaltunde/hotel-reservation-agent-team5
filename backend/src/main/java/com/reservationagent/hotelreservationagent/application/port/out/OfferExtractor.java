package com.reservationagent.hotelreservationagent.application.port.out;

import com.reservationagent.hotelreservationagent.domain.call.CallOutcome;
import com.reservationagent.hotelreservationagent.domain.task.StayDetails;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Turns a call transcript into structured fields (Spring AI). The output is untrusted: the
 * application re-validates every field and never lets the model compute totals.
 */
public interface OfferExtractor {

    ExtractedOffer extract(String transcript, StayDetails requestedStay);

    record ExtractedOffer(
            CallOutcome callOutcome,
            Boolean availabilityConfirmed,
            LocalDate checkIn,
            LocalDate checkOut,
            String roomType,
            Integer guestCount,
            String currency,
            BigDecimal roomSubtotal,
            BigDecimal hst,
            BigDecimal municipalAccommodationTax,
            BigDecimal mandatoryFees,
            BigDecimal optionalFees,
            BigDecimal allInTotal,
            String taxesIncludedInQuote,
            BigDecimal initialQuotedTotal,
            String cancellationTerms,
            String depositTerms,
            String enquiryId,
            Instant quoteValidUntil,
            Boolean roomHeld,
            Boolean reservationCreated,
            String confirmationNumber,
            Boolean writtenConfirmationPromised,
            String failureReason
    ) {
    }
}
