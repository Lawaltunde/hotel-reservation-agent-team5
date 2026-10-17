package com.reservationagent.hotelreservationagent.unit.domain;

import com.reservationagent.hotelreservationagent.domain.task.DateExpression;
import com.reservationagent.hotelreservationagent.domain.task.DateExpression.Kind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Dates are labelled by the model and resolved here; "today" is a parameter, so no clock is needed. */
class DateExpressionTest {

    private static final LocalDate WEDNESDAY = LocalDate.parse("2026-11-04");
    private static final LocalDate SATURDAY = LocalDate.parse("2026-11-07");
    private static final LocalDate SUNDAY = LocalDate.parse("2026-11-08");

    private static DateExpression of(Kind kind) {
        return new DateExpression(kind, null, null, null, null, null);
    }

    private static DateExpression weekday(Kind kind, DayOfWeek day) {
        return new DateExpression(kind, null, null, null, day, null);
    }

    private static DateExpression absolute(Integer year, Integer month, Integer day) {
        return new DateExpression(Kind.ABSOLUTE, year, month, day, null, null);
    }

    @Test
    void todayTomorrowAndInDaysCountFromToday() {
        LocalDate laterReference = WEDNESDAY.plusDays(10);
        assertThat(of(Kind.TODAY).resolve(WEDNESDAY, laterReference)).contains(WEDNESDAY);
        assertThat(of(Kind.TOMORROW).resolve(WEDNESDAY, laterReference)).contains(WEDNESDAY.plusDays(1));
        assertThat(new DateExpression(Kind.IN_DAYS, null, null, null, null, 3).resolve(WEDNESDAY, laterReference))
                .contains(WEDNESDAY.plusDays(3));
        assertThat(new DateExpression(Kind.IN_DAYS, null, null, null, null, -1).resolve(WEDNESDAY, WEDNESDAY))
                .as("yesterday resolves; validation rejects it").contains(WEDNESDAY.minusDays(1));
    }

    @ParameterizedTest(name = "{0} {1} from {2} -> {3}")
    @CsvSource({
            // "Friday" / "this Friday": on or after the reference
            "THIS_WEEKDAY,FRIDAY,2026-11-04,2026-11-06",
            "THIS_WEEKDAY,WEDNESDAY,2026-11-04,2026-11-04",
            "THIS_WEEKDAY,FRIDAY,2026-11-07,2026-11-13",
            "THIS_WEEKDAY,SATURDAY,2026-11-07,2026-11-07",
            "THIS_WEEKDAY,SUNDAY,2026-11-07,2026-11-08",
            // "next Friday": strictly after the reference
            "NEXT_WEEKDAY,FRIDAY,2026-11-04,2026-11-06",
            "NEXT_WEEKDAY,WEDNESDAY,2026-11-04,2026-11-11",
            "NEXT_WEEKDAY,SATURDAY,2026-11-07,2026-11-14",
            "NEXT_WEEKDAY,MONDAY,2026-11-07,2026-11-09"})
    void weekdaysResolveFromTheReference(Kind kind, DayOfWeek day, LocalDate reference, LocalDate expected) {
        assertThat(weekday(kind, day).resolve(reference, reference)).contains(expected);
    }

    @Test
    void thisWeekendIsTheComingFridayOrTodayIfAlreadyTheWeekend() {
        assertThat(of(Kind.THIS_WEEKEND).resolve(WEDNESDAY, WEDNESDAY)).contains(LocalDate.parse("2026-11-06"));
        assertThat(of(Kind.THIS_WEEKEND).resolve(LocalDate.parse("2026-11-06"), LocalDate.parse("2026-11-06")))
                .as("on Friday").contains(LocalDate.parse("2026-11-06"));
        assertThat(of(Kind.THIS_WEEKEND).resolve(SATURDAY, SATURDAY)).as("on Saturday").contains(SATURDAY);
        assertThat(of(Kind.THIS_WEEKEND).resolve(SUNDAY, SUNDAY)).as("on Sunday").contains(SUNDAY);
    }

    @Test
    void nextWeekendIsTheFirstFridayStrictlyAfterTheReference() {
        assertThat(of(Kind.NEXT_WEEKEND).resolve(WEDNESDAY, WEDNESDAY)).contains(LocalDate.parse("2026-11-06"));
        assertThat(of(Kind.NEXT_WEEKEND).resolve(SATURDAY, SATURDAY)).contains(LocalDate.parse("2026-11-13"));
        assertThat(of(Kind.NEXT_WEEKEND).resolve(LocalDate.parse("2026-11-06"), LocalDate.parse("2026-11-06")))
                .as("on Friday it is next week's").contains(LocalDate.parse("2026-11-13"));
    }

    @Test
    void absoluteDateWithAYearIsTakenAsWritten() {
        assertThat(absolute(2027, 2, 14).resolve(WEDNESDAY, WEDNESDAY)).contains(LocalDate.parse("2027-02-14"));
        assertThat(absolute(2025, 1, 1).resolve(WEDNESDAY, WEDNESDAY))
                .as("a past year resolves; validation rejects it").contains(LocalDate.parse("2025-01-01"));
    }

    @Test
    void absoluteDateWithoutAYearIsTheNextOccurrenceOnOrAfterTheReference() {
        assertThat(absolute(null, 11, 20).resolve(WEDNESDAY, WEDNESDAY)).contains(LocalDate.parse("2026-11-20"));
        assertThat(absolute(null, 11, 4).resolve(WEDNESDAY, WEDNESDAY)).as("today").contains(WEDNESDAY);
        assertThat(absolute(null, 1, 5).resolve(WEDNESDAY, WEDNESDAY)).as("rolls into next year")
                .contains(LocalDate.parse("2027-01-05"));
        assertThat(absolute(null, 11, 4).resolve(WEDNESDAY, WEDNESDAY.plusDays(1)))
                .as("check-out reference is the day after check-in").contains(LocalDate.parse("2027-11-04"));
    }

    @Test
    void impossibleOrIncompleteDatesAreEmpty() {
        assertThat(absolute(null, 2, 30).resolve(WEDNESDAY, WEDNESDAY)).isEmpty();
        assertThat(absolute(2027, 13, 1).resolve(WEDNESDAY, WEDNESDAY)).isEmpty();
        assertThat(absolute(null, null, 20).resolve(WEDNESDAY, WEDNESDAY)).isEmpty();
        assertThat(absolute(null, 11, null).resolve(WEDNESDAY, WEDNESDAY)).isEmpty();
        assertThat(weekday(Kind.THIS_WEEKDAY, null).resolve(WEDNESDAY, WEDNESDAY)).isEmpty();
        assertThat(weekday(Kind.NEXT_WEEKDAY, null).resolve(WEDNESDAY, WEDNESDAY)).isEmpty();
        assertThat(new DateExpression(Kind.IN_DAYS, null, null, null, null, null).resolve(WEDNESDAY, WEDNESDAY)).isEmpty();
        assertThat(of(null).resolve(WEDNESDAY, WEDNESDAY)).isEmpty();
    }

    @Test
    void february29WithAYearOnlyInLeapYears() {
        assertThat(absolute(2028, 2, 29).resolve(WEDNESDAY, WEDNESDAY)).contains(LocalDate.parse("2028-02-29"));
        assertThat(absolute(2027, 2, 29).resolve(WEDNESDAY, WEDNESDAY)).isEmpty();
    }

    @Test
    void february29WithoutAYearIsTheNextFebruary29NeverFebruary28() {
        assertThat(absolute(null, 2, 29).resolve(WEDNESDAY, WEDNESDAY)).contains(LocalDate.parse("2028-02-29"));
    }
}
