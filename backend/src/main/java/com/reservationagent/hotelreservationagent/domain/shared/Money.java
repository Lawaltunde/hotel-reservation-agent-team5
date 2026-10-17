package com.reservationagent.hotelreservationagent.domain.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * An exact CAD amount. The agent is scoped to Ontario, so every comparable price is in CAD;
 * a quote in any other currency is rejected rather than converted.
 */
public record Money(BigDecimal amount) implements Comparable<Money> {

    public static final String CURRENCY = "CAD";
    public static final Money ZERO = new Money(BigDecimal.ZERO);

    public Money {
        Objects.requireNonNull(amount, "amount");
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    public static Money cad(String amount) {
        return new Money(new BigDecimal(amount));
    }

    public static Money cad(BigDecimal amount) {
        return new Money(amount);
    }

    public Money plus(Money other) {
        return new Money(amount.add(other.amount));
    }

    public Money minus(Money other) {
        return new Money(amount.subtract(other.amount));
    }

    public Money times(BigDecimal factor) {
        return new Money(amount.multiply(factor));
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    @Override
    public int compareTo(Money other) {
        return amount.compareTo(other.amount);
    }

    @Override
    public String toString() {
        return CURRENCY + " " + amount.toPlainString();
    }
}
