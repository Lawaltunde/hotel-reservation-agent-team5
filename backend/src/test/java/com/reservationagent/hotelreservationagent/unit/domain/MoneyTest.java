package com.reservationagent.hotelreservationagent.unit.domain;

import com.reservationagent.hotelreservationagent.domain.shared.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class MoneyTest {

    @Test
    void amountsAreKeptToTwoDecimalsRoundingHalfUp() {
        assertThat(Money.cad("10").amount()).isEqualByComparingTo("10.00").hasToString("10.00");
        assertThat(Money.cad("10.005").amount()).hasToString("10.01");
        assertThat(Money.cad("10.004").amount()).hasToString("10.00");
    }

    @Test
    void equalityIgnoresTheInputScale() {
        assertThat(Money.cad("199.9")).isEqualTo(Money.cad("199.90"));
        assertThat(Money.cad(new BigDecimal("5"))).isEqualTo(Money.cad("5.00"));
    }

    @Test
    void arithmeticIsExact() {
        assertThat(Money.cad("0.10").plus(Money.cad("0.20"))).isEqualTo(Money.cad("0.30"));
        assertThat(Money.cad("100.00").minus(Money.cad("120.50"))).isEqualTo(Money.cad("-20.50"));
        assertThat(Money.cad("200.00").times(new BigDecimal("0.13"))).isEqualTo(Money.cad("26.00"));
    }

    @Test
    void reportsNegativeAmounts() {
        assertThat(Money.cad("-0.01").isNegative()).isTrue();
        assertThat(Money.ZERO.isNegative()).isFalse();
    }

    @Test
    void ordersByAmount() {
        assertThat(Money.cad("99.99")).isLessThan(Money.cad("100.00"));
        assertThat(Money.cad("100")).isEqualByComparingTo(Money.cad("100.00"));
    }

    @Test
    void printsTheCurrency() {
        assertThat(Money.cad("1234.5")).hasToString("CAD 1234.50");
        assertThat(Money.CURRENCY).isEqualTo("CAD");
    }

    @Test
    void rejectsNull() {
        assertThatNullPointerException().isThrownBy(() -> new Money(null));
    }
}
