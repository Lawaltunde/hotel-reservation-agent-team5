package com.reservationagent.hotelreservationagent.unit.domain;

import com.reservationagent.hotelreservationagent.domain.hotel.PhoneNumber;
import com.reservationagent.hotelreservationagent.domain.shared.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class PhoneNumberTest {

    @Test
    void acceptsANanpNumberInE164() {
        PhoneNumber phone = new PhoneNumber("+16135550100");
        assertThat(phone.areaCode()).isEqualTo("613");
        assertThat(phone.isTollFree()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"+18005550100", "+18335550100", "+18445550100", "+18555550100", "+18665550100",
            "+18775550100", "+18885550100"})
    void recognisesTollFreeNumbers(String e164) {
        assertThat(new PhoneNumber(e164).isTollFree()).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "6135550100", "16135550100", "+1613555010", "+161355501000", "+16131550100",
            "+11135550100", "+446135550100", "+1 613 555 0100", "+1613-555-0100"})
    void rejectsAnythingElse(String e164) {
        assertThatExceptionOfType(DomainException.class).isThrownBy(() -> new PhoneNumber(e164));
    }
}
