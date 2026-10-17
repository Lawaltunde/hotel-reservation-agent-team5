package com.reservationagent.hotelreservationagent.unit.application;

import com.reservationagent.hotelreservationagent.application.service.pricing.OfferEvaluator;
import com.reservationagent.hotelreservationagent.domain.offer.HotelOffer;
import com.reservationagent.hotelreservationagent.domain.shared.Money;
import com.reservationagent.hotelreservationagent.domain.task.AuthorizationMode;
import com.reservationagent.hotelreservationagent.domain.task.BookingTask;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static com.reservationagent.hotelreservationagent.support.Fixtures.CLOCK;
import static com.reservationagent.hotelreservationagent.support.Fixtures.NOW;
import static com.reservationagent.hotelreservationagent.support.Fixtures.breakdown;
import static com.reservationagent.hotelreservationagent.support.Fixtures.offer;
import static com.reservationagent.hotelreservationagent.support.Fixtures.task;
import static com.reservationagent.hotelreservationagent.support.Fixtures.unknownBreakdown;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Winner selection. Quote-expiry and PriceBreakdown arithmetic are covered separately once the
 * phase 2.1 changes land; these offers all have every price component stated.
 */
class OfferEvaluatorTest {

    private final OfferEvaluator evaluator = new OfferEvaluator(CLOCK);
    private final BookingTask task = task();

    /** comparable total = 200 + 26 + 8 + fees */
    private HotelOffer offerTotalling(String fees, int receivedSecondsAfterNow) {
        return offer(task, UUID.randomUUID(), breakdown("200.00", "26.00", "8.00", fees), NOW.plusSeconds(receivedSecondsAfterNow));
    }

    private static HotelOffer with(HotelOffer o, boolean available, java.time.LocalDate in, java.time.LocalDate out, int guests) {
        return new HotelOffer(o.id(), o.taskId(), o.hotelId(), o.callId(), available, in, out, o.roomType(), guests,
                o.initialQuotedTotal(), o.negotiated(), o.cancellationTerms(), o.depositTerms(), o.enquiryId(),
                o.quoteValidity(), o.holdsRoom(), o.evidenceStatus(), o.receivedAt());
    }

    @Test
    void exactMatchWithKnownTotalIsComparable() {
        assertThat(evaluator.isComparable(task, offerTotalling("0", 0))).isTrue();
    }

    @Test
    void unconfirmedAvailabilityIsNotComparable() {
        HotelOffer o = offerTotalling("0", 0);
        assertThat(evaluator.isComparable(task, with(o, false, o.checkIn(), o.checkOut(), o.guestCount()))).isFalse();
    }

    @Test
    void differentDatesAreNotComparable() {
        HotelOffer o = offerTotalling("0", 0);
        assertThat(evaluator.isComparable(task, with(o, true, o.checkIn().plusDays(1), o.checkOut(), o.guestCount()))).isFalse();
        assertThat(evaluator.isComparable(task, with(o, true, o.checkIn(), o.checkOut().plusDays(1), o.guestCount()))).isFalse();
        assertThat(evaluator.isComparable(task, with(o, true, null, null, o.guestCount()))).isFalse();
    }

    @Test
    void roomForFewerGuestsIsNotComparableButMoreIsFine() {
        HotelOffer o = offerTotalling("0", 0);
        int guests = task.stay().guests();
        assertThat(evaluator.isComparable(task, with(o, true, o.checkIn(), o.checkOut(), guests - 1))).isFalse();
        assertThat(evaluator.isComparable(task, with(o, true, o.checkIn(), o.checkOut(), guests + 1))).isTrue();
    }

    @Test
    void unknownTotalIsNotComparable() {
        assertThat(evaluator.isComparable(task, offer(task, UUID.randomUUID(), unknownBreakdown(), NOW))).isFalse();
    }

    @Test
    void budgetIsInclusiveAndAbsentBudgetAllowsAnything() {
        HotelOffer at234 = offerTotalling("0", 0);
        assertThat(evaluator.isWithinBudget(task, at234)).isTrue();
        assertThat(evaluator.isWithinBudget(task(AuthorizationMode.ENQUIRY_ONLY, Money.cad("234.00")), at234)).isTrue();
        assertThat(evaluator.isWithinBudget(task(AuthorizationMode.ENQUIRY_ONLY, Money.cad("233.99")), at234)).isFalse();
    }

    @Test
    void unknownTotalIsNeverWithinABudget() {
        HotelOffer unknown = offer(task, UUID.randomUUID(), unknownBreakdown(), NOW);
        assertThat(evaluator.isWithinBudget(task(AuthorizationMode.ENQUIRY_ONLY, Money.cad("100000")), unknown)).isFalse();
    }

    @Test
    void bestIsTheCheapestComparableTotalNotTheCheapestRoomRate() {
        HotelOffer cheapRoomBigFees = offer(task, UUID.randomUUID(), breakdown("150.00", "19.50", "6.00", "80.00"), NOW);
        HotelOffer allIn240 = offer(task, UUID.randomUUID(), breakdown("200.00", "26.00", "8.00", "6.00"), NOW);

        assertThat(evaluator.best(task, List.of(cheapRoomBigFees, allIn240))).contains(allIn240);
    }

    @Test
    void bestSkipsNonComparableAndOverBudgetOffers() {
        BookingTask budget = task(AuthorizationMode.ENQUIRY_ONLY, Money.cad("250"));
        HotelOffer unknown = offer(budget, UUID.randomUUID(), unknownBreakdown(), NOW);
        HotelOffer overBudget = offer(budget, UUID.randomUUID(), breakdown("230.00", "29.90", "0", "0"), NOW);
        HotelOffer ok = offer(budget, UUID.randomUUID(), breakdown("210.00", "27.30", "0", "0"), NOW);

        assertThat(evaluator.best(budget, List.of(unknown, overBudget, ok))).contains(ok);
        assertThat(evaluator.best(budget, List.of(unknown, overBudget))).isEmpty();
    }

    @Test
    void equalTotalsGoToTheEarlierQuote() {
        HotelOffer later = offerTotalling("10.00", 60);
        HotelOffer earlier = offerTotalling("10.00", 5);
        assertThat(evaluator.best(task, List.of(later, earlier))).contains(earlier);
    }

    @Test
    void noOffersMeansNoWinner() {
        assertThat(evaluator.best(task, List.of())).isEmpty();
    }
}
