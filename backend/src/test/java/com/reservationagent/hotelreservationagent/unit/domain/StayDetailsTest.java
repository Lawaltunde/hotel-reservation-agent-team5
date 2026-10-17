package com.reservationagent.hotelreservationagent.unit.domain;

import com.reservationagent.hotelreservationagent.domain.shared.DomainException;
import com.reservationagent.hotelreservationagent.domain.task.StayDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class StayDetailsTest {

    private static final LocalDate IN = LocalDate.parse("2026-11-20");

    @Test
    void countsNightsAndGuests() {
        StayDetails stay = new StayDetails(IN, IN.plusDays(3), 2, 3, 1);
        assertThat(stay.nights()).isEqualTo(3);
        assertThat(stay.guests()).isEqualTo(4);
    }

    @Test
    void nightsSpanMonthAndDstBoundaries() {
        // Ontario leaves DST on 2026-11-01; nights are calendar days, not 24h blocks.
        assertThat(new StayDetails(LocalDate.parse("2026-10-31"), LocalDate.parse("2026-11-02"), 1, 1, 0).nights())
                .isEqualTo(2);
    }

    @Test
    void checkOutMustBeAfterCheckIn() {
        assertThatExceptionOfType(DomainException.class).isThrownBy(() -> new StayDetails(IN, IN, 1, 1, 0));
        assertThatExceptionOfType(DomainException.class).isThrownBy(() -> new StayDetails(IN, IN.minusDays(1), 1, 1, 0));
        assertThatExceptionOfType(DomainException.class).isThrownBy(() -> new StayDetails(null, IN, 1, 1, 0));
        assertThatExceptionOfType(DomainException.class).isThrownBy(() -> new StayDetails(IN, null, 1, 1, 0));
    }

    @ParameterizedTest
    @CsvSource({"0,1,0", "1,0,0", "1,1,-1"})
    void roomsAndAdultsMustBePositiveAndChildrenNonNegative(int rooms, int adults, int children) {
        assertThatExceptionOfType(DomainException.class)
                .isThrownBy(() -> new StayDetails(IN, IN.plusDays(1), rooms, adults, children));
    }
}
