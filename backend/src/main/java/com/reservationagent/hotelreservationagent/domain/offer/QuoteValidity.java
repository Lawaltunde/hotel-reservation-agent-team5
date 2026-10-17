package com.reservationagent.hotelreservationagent.domain.offer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

/**
 * How long the hotel said a quote is valid. The language model only copies what was said
 * ({@code statedText}) and, if the hotel gave a period, its length in hours; the expiry instant
 * is computed here from the end of the call, never produced by the model.
 *
 * @param statedText  the hotel's words about validity, verbatim, or {@code null} if not discussed
 * @param statedHours the stated validity period in hours, or {@code null} if none was given or it was implausible
 * @param validUntil  call end + {@code statedHours}, or {@code null} when unknown
 */
public record QuoteValidity(String statedText, BigDecimal statedHours, Instant validUntil) {

    public static final QuoteValidity UNKNOWN = new QuoteValidity(null, null, null);

    /** A phone quote held longer than this is implausible; such a period is discarded as unknown. */
    public static final BigDecimal MAX_HOURS = BigDecimal.valueOf(30 * 24);

    /**
     * Builds the validity from extracted, untrusted values. A non-positive or implausibly long
     * period is dropped, leaving the expiry unknown.
     */
    public static QuoteValidity stated(String text, BigDecimal hours, Instant callEndedAt) {
        String statedText = text == null || text.isBlank() ? null : text.strip();
        if (hours == null || callEndedAt == null || hours.signum() <= 0 || hours.compareTo(MAX_HOURS) > 0) {
            return new QuoteValidity(statedText, null, null);
        }
        long minutes = hours.multiply(BigDecimal.valueOf(60)).setScale(0, RoundingMode.DOWN).longValueExact();
        return new QuoteValidity(statedText, hours, callEndedAt.plus(Duration.ofMinutes(minutes)));
    }
}
