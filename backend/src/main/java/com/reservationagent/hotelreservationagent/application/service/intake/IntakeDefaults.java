package com.reservationagent.hotelreservationagent.application.service.intake;

import com.reservationagent.hotelreservationagent.application.port.out.TaskPromptInterpreter.Budget;
import com.reservationagent.hotelreservationagent.application.port.out.TaskPromptInterpreter.BudgetUnit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Every rule for what a one-shot prompt did not say lives here. Unstated values are never presented
 * as the user's: the task marks them unstated, the voice agent asks hotels neutrally, and each
 * assumption is listed for the UI and the report.
 */
public final class IntakeDefaults {

    /** Placeholders for the search when unstated; the voice agent is told they are not specified. */
    public static final int ROOMS = 1;
    public static final int ADULTS = 1;
    public static final int CHILDREN = 0;

    public static final int NIGHTS_WHEN_NO_CHECK_OUT = 1;
    /** "This weekend" or "next weekend" with no check-out or length means Friday and Saturday night. */
    public static final int NIGHTS_FOR_WEEKEND = 2;

    /** An UNKNOWN-unit budget below this, for a stay of more than one night, is read as per night. */
    static final BigDecimal PER_NIGHT_THRESHOLD = BigDecimal.valueOf(600);

    private IntakeDefaults() {
    }

    /** The dates hotels are asked about, and what was assumed to get them. */
    public record Stay(LocalDate checkIn, LocalDate checkOut, List<String> assumptions) {
    }

    /**
     * PENDING the user's decision: with no check-in date at all, hotels are asked about tonight for one
     * night. Change the no-date rule here only.
     *
     * @param checkIn  the stated check-in, or {@code null}
     * @param checkOut the stated check-out, or {@code null}
     * @param nights   the stated length of stay, or {@code null}
     */
    static Stay stay(LocalDate today, LocalDate checkIn, LocalDate checkOut, Integer nights, boolean weekend) {
        List<String> assumptions = new ArrayList<>();
        LocalDate in = checkIn;
        if (in == null) {
            in = today;
            assumptions.add("No dates given: asking hotels for tonight (" + today + ")");
        }
        LocalDate out = checkOut;
        if (out == null && nights != null && nights > 0) {
            out = in.plusDays(nights);
        }
        if (out == null) {
            int n = weekend ? NIGHTS_FOR_WEEKEND : NIGHTS_WHEN_NO_CHECK_OUT;
            out = in.plusDays(n);
            assumptions.add("No check-out or length of stay given: asking for " + n + " night" + (n == 1 ? "" : "s"));
        }
        return new Stay(in, out, assumptions);
    }

    /**
     * The whole-stay budget. PER_NIGHT is multiplied by the nights; PER_STAY is used as given; UNKNOWN
     * is read as per night when it is under {@link #PER_NIGHT_THRESHOLD} and the stay is longer than
     * one night, otherwise as the stay total.
     */
    static BigDecimal stayTotal(Budget budget, long nights) {
        if (budget == null || budget.amount() == null) return null;
        return isPerNight(budget, nights) ? budget.amount().multiply(BigDecimal.valueOf(nights)) : budget.amount();
    }

    /** The nightly budget, only when the guest said "per night". */
    static BigDecimal perNight(Budget budget) {
        return budget != null && budget.amount() != null && budget.unit() == BudgetUnit.PER_NIGHT ? budget.amount() : null;
    }

    private static boolean isPerNight(Budget budget, long nights) {
        BudgetUnit unit = budget.unit() == null ? BudgetUnit.UNKNOWN : budget.unit();
        return switch (unit) {
            case PER_NIGHT -> true;
            case PER_STAY -> false;
            case UNKNOWN -> nights > 1 && budget.amount().compareTo(PER_NIGHT_THRESHOLD) < 0;
        };
    }
}
