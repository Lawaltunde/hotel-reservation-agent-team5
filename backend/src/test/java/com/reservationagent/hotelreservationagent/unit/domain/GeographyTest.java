package com.reservationagent.hotelreservationagent.unit.domain;

import com.reservationagent.hotelreservationagent.domain.geography.GeoPoint;
import com.reservationagent.hotelreservationagent.domain.geography.Municipality;
import com.reservationagent.hotelreservationagent.domain.shared.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

class GeographyTest {

    @ParameterizedTest
    @CsvSource({"90,180", "-90,-180", "0,0", "45.4215,-75.6972"})
    void acceptsValidCoordinates(double lat, double lon) {
        assertThatNoException().isThrownBy(() -> new GeoPoint(lat, lon));
    }

    @ParameterizedTest
    @CsvSource({"90.0001,0", "-90.0001,0", "0,180.0001", "0,-180.0001"})
    void rejectsCoordinatesOutOfRange(double lat, double lon) {
        assertThatExceptionOfType(DomainException.class).isThrownBy(() -> new GeoPoint(lat, lon));
    }

    @Test
    void municipalityAliasesAreNeverNullAndAreCopied() {
        Municipality none = new Municipality(UUID.randomUUID(), "Ottawa", null, null, new GeoPoint(45, -75),
                ZoneId.of("America/Toronto"), null);
        assertThat(none.aliases()).isEmpty();

        Set<String> aliases = new HashSet<>(Set.of("sudbury"));
        Municipality sudbury = new Municipality(UUID.randomUUID(), "Greater Sudbury", null, aliases,
                new GeoPoint(46.49, -80.99), ZoneId.of("America/Toronto"), null);
        aliases.add("nickel city");
        assertThat(sudbury.aliases()).containsExactly("sudbury");
        assertThat(Municipality.PROVINCE_CODE).isEqualTo("CA-ON");
    }
}
