package com.reservationagent.hotelreservationagent.application.service.intake;

/**
 * What a free-text request gets when it leaves something out. This is the one place to change
 * these rules; the response's {@code request} object shows the user what was assumed.
 */
public final class IntakeDefaults {

    public static final int ROOMS = 1;
    public static final int ADULTS = 1;
    public static final int CHILDREN = 0;

    /**
     * A request with a check-in but no check-out or length of stay is for this many nights. A
     * request without a check-in is rejected: the arrival date is never assumed.
     */
    public static final int NIGHTS_WHEN_NO_CHECK_OUT = 1;

    /** "This weekend" or "next weekend" with no check-out or length means Friday and Saturday night. */
    public static final int NIGHTS_FOR_WEEKEND = 2;

    private IntakeDefaults() {
    }
}
