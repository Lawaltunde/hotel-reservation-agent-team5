package com.reservationagent.hotelreservationagent.unit.domain;

import com.reservationagent.hotelreservationagent.domain.offer.QuoteValidity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Quote expiry is computed in Java from the call end and the period the hotel stated. */
class QuoteValidityTest {

    private static final Instant CALL_END = Instant.parse("2026-11-02T17:15:00Z");

    @Test
    void expiryIsCallEndPlusTheStatedPeriod() {
        QuoteValidity v = QuoteValidity.stated("  We can hold it for 24 hours ", new BigDecimal("24"), CALL_END);
        assertThat(v.statedText()).isEqualTo("We can hold it for 24 hours");
        assertThat(v.statedHours()).isEqualByComparingTo("24");
        assertThat(v.validUntil()).isEqualTo(Instant.parse("2026-11-03T17:15:00Z"));
    }

    @Test
    void fractionalHoursRoundDownToTheMinute() {
        assertThat(QuoteValidity.stated("90 minutes", new BigDecimal("1.5"), CALL_END).validUntil())
                .isEqualTo(Instant.parse("2026-11-02T18:45:00Z"));
        // 0.0175 h = 1.05 min -> 1 min, never rounded up past what the hotel said
        assertThat(QuoteValidity.stated("a minute", new BigDecimal("0.0175"), CALL_END).validUntil())
                .isEqualTo(Instant.parse("2026-11-02T17:16:00Z"));
    }

    @Test
    void thirtyDaysIsTheLongestPlausiblePeriod() {
        assertThat(QuoteValidity.MAX_HOURS).isEqualByComparingTo("720");
        assertThat(QuoteValidity.stated(null, new BigDecimal("720"), CALL_END).validUntil())
                .isEqualTo(CALL_END.plusSeconds(720 * 3600));
        QuoteValidity tooLong = QuoteValidity.stated("valid for a year", new BigDecimal("720.01"), CALL_END);
        assertThat(tooLong.validUntil()).isNull();
        assertThat(tooLong.statedHours()).isNull();
        assertThat(tooLong.statedText()).isEqualTo("valid for a year");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "-0.5"})
    void nonPositivePeriodsAreUnknown(String hours) {
        QuoteValidity v = QuoteValidity.stated("until tonight", new BigDecimal(hours), CALL_END);
        assertThat(v.validUntil()).isNull();
        assertThat(v.statedHours()).isNull();
        assertThat(v.statedText()).isEqualTo("until tonight");
    }

    @Test
    void withoutACallEndOrAPeriodTheExpiryIsUnknown() {
        assertThat(QuoteValidity.stated("24 hours", new BigDecimal("24"), null).validUntil()).isNull();
        assertThat(QuoteValidity.stated("only today", null, CALL_END).validUntil()).isNull();
    }

    @Test
    void blankTextIsNotStated() {
        assertThat(QuoteValidity.stated("   ", null, CALL_END).statedText()).isNull();
        assertThat(QuoteValidity.stated(null, null, null)).isEqualTo(QuoteValidity.UNKNOWN);
    }
}
