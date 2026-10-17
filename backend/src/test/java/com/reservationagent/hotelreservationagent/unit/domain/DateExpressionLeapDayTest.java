package com.reservationagent.hotelreservationagent.unit.domain;

import com.reservationagent.hotelreservationagent.domain.task.DateExpression;
import com.reservationagent.hotelreservationagent.domain.task.DateExpression.Kind;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Feb 29 without a year is the next real Feb 29, never Feb 28. */
class DateExpressionLeapDayTest {

    private static DateExpression feb29(Integer year) {
        return new DateExpression(Kind.ABSOLUTE, year, 2, 29, null, null);
    }

    @Test
    void february29WithoutAYearIsTheNextLeapDay() {
        LocalDate today = LocalDate.parse("2026-10-17");
        assertThat(feb29(null).resolve(today, today)).contains(LocalDate.parse("2028-02-29"));
    }

    @Test
    void february29InALeapYearBeforeItIsThatYear() {
        LocalDate today = LocalDate.parse("2028-01-10");
        assertThat(feb29(null).resolve(today, today)).contains(LocalDate.parse("2028-02-29"));
    }

    @Test
    void february29OfANonLeapYearDoesNotExist() {
        LocalDate today = LocalDate.parse("2026-10-17");
        assertThat(feb29(2027).resolve(today, today)).isEmpty();
    }

    @Test
    void ordinaryDatesStillRollToNextYear() {
        LocalDate today = LocalDate.parse("2026-10-17");
        assertThat(new DateExpression(Kind.ABSOLUTE, null, 3, 1, null, null).resolve(today, today))
                .contains(LocalDate.parse("2027-03-01"));
    }
}
