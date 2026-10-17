package com.reservationagent.hotelreservationagent.domain.task;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;

/**
 * A date as a guest wrote it ("next Friday", "Nov 20", "tomorrow"), described without resolving it.
 * A language model fills these fields; Java turns them into a calendar date, so the model never
 * does date arithmetic.
 *
 * @param kind    how the date was expressed
 * @param year    for {@link Kind#ABSOLUTE}, the year if one was written, else {@code null}
 * @param month   for {@link Kind#ABSOLUTE}, 1-12
 * @param day     for {@link Kind#ABSOLUTE}, day of month
 * @param weekday for {@link Kind#THIS_WEEKDAY} and {@link Kind#NEXT_WEEKDAY}
 * @param days    for {@link Kind#IN_DAYS}, the number of days from today (negative for the past)
 */
public record DateExpression(Kind kind, Integer year, Integer month, Integer day, DayOfWeek weekday, Integer days) {

    public enum Kind {
        /** A calendar date, e.g. "Nov 20" or "2026-11-20". */
        ABSOLUTE,
        TODAY,
        TOMORROW,
        /** "in 3 days"; negative for a past date ("yesterday" = -1), which validation then rejects. */
        IN_DAYS,
        /** "Friday", "this Friday", "on Friday": the first such day on or after the reference date. */
        THIS_WEEKDAY,
        /** "next Friday": the first such day strictly after the reference date. */
        NEXT_WEEKDAY,
        /** "this weekend": today if it is already Saturday or Sunday, else the coming Friday. */
        THIS_WEEKEND,
        /** "next weekend": the first Friday strictly after the reference date. */
        NEXT_WEEKEND
    }

    /**
     * Resolves the expression.
     *
     * @param today     today's date in Ontario; {@code TODAY}, {@code TOMORROW} and {@code IN_DAYS} count from it
     * @param reference the earliest acceptable date for a weekday or a date written without a year,
     *                  e.g. today for a check-in, the day after check-in for a check-out
     * @return the date, or empty if the fields are missing or describe no real date (e.g. February 30)
     */
    public Optional<LocalDate> resolve(LocalDate today, LocalDate reference) {
        if (kind == null) {
            return Optional.empty();
        }
        try {
            return switch (kind) {
                case TODAY -> Optional.of(today);
                case TOMORROW -> Optional.of(today.plusDays(1));
                case IN_DAYS -> days == null ? Optional.empty() : Optional.of(today.plusDays(days));
                case THIS_WEEKDAY -> weekday == null ? Optional.empty()
                        : Optional.of(reference.with(TemporalAdjusters.nextOrSame(weekday)));
                case NEXT_WEEKDAY -> weekday == null ? Optional.empty()
                        : Optional.of(reference.with(TemporalAdjusters.next(weekday)));
                case THIS_WEEKEND -> Optional.of(reference.getDayOfWeek() == DayOfWeek.SATURDAY
                        || reference.getDayOfWeek() == DayOfWeek.SUNDAY
                        ? reference : reference.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY)));
                case NEXT_WEEKEND -> Optional.of(reference.with(TemporalAdjusters.next(DayOfWeek.FRIDAY)));
                case ABSOLUTE -> absolute(reference);
            };
        } catch (DateTimeException e) {
            return Optional.empty();
        }
    }

    /** A date with no year is the first occurrence on or after {@code reference}. */
    private Optional<LocalDate> absolute(LocalDate reference) {
        if (month == null || day == null) {
            return Optional.empty();
        }
        if (year != null) {
            return Optional.of(LocalDate.of(year, month, day));
        }
        MonthDay monthDay = MonthDay.of(month, day);
        LocalDate candidate = monthDay.atYear(reference.getYear());
        if (candidate.isBefore(reference)) {
            candidate = monthDay.atYear(reference.getYear() + 1);
        }
        return Optional.of(candidate);
    }
}
