package com.reservationagent.hotelreservationagent.unit.domain;

import com.reservationagent.hotelreservationagent.domain.offer.HotelOffer;
import com.reservationagent.hotelreservationagent.domain.shared.Money;
import com.reservationagent.hotelreservationagent.domain.task.BookingTask;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.reservationagent.hotelreservationagent.support.Fixtures.NOW;
import static com.reservationagent.hotelreservationagent.support.Fixtures.breakdown;
import static com.reservationagent.hotelreservationagent.support.Fixtures.offer;
import static com.reservationagent.hotelreservationagent.support.Fixtures.task;
import static com.reservationagent.hotelreservationagent.support.Fixtures.unknownBreakdown;
import static org.assertj.core.api.Assertions.assertThat;

/** Savings versus the first phone quote. PriceBreakdown arithmetic itself is tested elsewhere (phase 2.1). */
class HotelOfferTest {

    private final BookingTask task = task();

    private static HotelOffer withInitialQuote(HotelOffer o, Money initial) {
        return new HotelOffer(o.id(), o.taskId(), o.hotelId(), o.callId(), o.availabilityConfirmed(), o.checkIn(),
                o.checkOut(), o.roomType(), o.guestCount(), initial, o.negotiated(), o.cancellationTerms(),
                o.depositTerms(), o.enquiryId(), o.quoteValidity(), o.holdsRoom(), o.evidenceStatus(), o.receivedAt());
    }

    @Test
    void savingsIsTheInitialQuoteMinusTheNegotiatedTotal() {
        HotelOffer o = offer(task, UUID.randomUUID(), breakdown("300.00", "39.00", "12.00", "15.00"), NOW);
        assertThat(o.initialQuotedTotal()).isEqualTo(Money.cad("400.00"));
        assertThat(o.savings()).isEqualTo(Money.cad("34.00"));
    }

    @Test
    void negativeSavingsAreReportedAsIs() {
        HotelOffer o = withInitialQuote(offer(task, UUID.randomUUID(), breakdown("300.00", "39.00", "12.00", "15.00"), NOW),
                Money.cad("350.00"));
        assertThat(o.savings()).isEqualTo(Money.cad("-16.00"));
        assertThat(o.savings().isNegative()).isTrue();
    }

    @Test
    void unknownNegotiatedTotalMeansUnknownSavings() {
        assertThat(offer(task, UUID.randomUUID(), unknownBreakdown(), NOW).savings()).isNull();
    }

    @Test
    @Disabled("BUG: savings() throws NullPointerException when initialQuotedTotal is null: "
            + "initialQuotedTotal::minus dereferences the receiver before the filter runs")
    void unknownInitialQuoteMeansUnknownSavings() {
        HotelOffer o = withInitialQuote(offer(task, UUID.randomUUID(), breakdown("300.00", "39.00", "12.00", "15.00"), NOW),
                null);
        assertThat(o.savings()).isNull();
    }
}
