package com.reservationagent.hotelreservationagent.unit.application;

import com.reservationagent.hotelreservationagent.application.port.in.SubmitBookingTaskUseCase.Command;
import com.reservationagent.hotelreservationagent.application.port.out.MunicipalityGeocoder;
import com.reservationagent.hotelreservationagent.application.port.out.MunicipalityRepository;
import com.reservationagent.hotelreservationagent.application.service.validation.BookingRequestValidator;
import com.reservationagent.hotelreservationagent.application.service.validation.OutOfScopeLocationException;
import com.reservationagent.hotelreservationagent.application.service.validation.RequestValidationException;
import com.reservationagent.hotelreservationagent.application.service.validation.RequestValidationException.FieldError;
import com.reservationagent.hotelreservationagent.domain.geography.GeoPoint;
import com.reservationagent.hotelreservationagent.domain.geography.Municipality;
import com.reservationagent.hotelreservationagent.domain.task.AuthorizationMode;
import com.reservationagent.hotelreservationagent.domain.task.TaskIntake;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.reservationagent.hotelreservationagent.support.Fixtures.CLOCK;
import static com.reservationagent.hotelreservationagent.support.Fixtures.NOW;
import static com.reservationagent.hotelreservationagent.support.Fixtures.ottawa;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingRequestValidatorTest {

    private static final LocalDate TODAY = LocalDate.now(CLOCK.withZone(Municipality.DEFAULT_TIME_ZONE));

    private final MunicipalityRepository municipalities = mock(MunicipalityRepository.class);
    private final MunicipalityGeocoder geocoder = mock(MunicipalityGeocoder.class);
    private final BookingRequestValidator validator = new BookingRequestValidator(municipalities, geocoder, CLOCK);

    @BeforeEach
    void ottawaExists() {
        when(municipalities.findByNameOrAlias(anyString())).thenReturn(List.of());
        when(municipalities.findByNameOrAlias("Ottawa")).thenReturn(List.of(ottawa()));
        when(municipalities.searchByPrefix(anyString(), anyInt())).thenReturn(List.of());
        when(geocoder.findOntarioLocality(anyString())).thenReturn(Optional.empty());
    }

    private static Command command(String city, LocalDate in, LocalDate out, int rooms, int adults, int children,
                                   String email, Instant deadline, BigDecimal max) {
        return new Command("req-1", city, in, out, rooms, adults, children, email, deadline, Set.of(),
                AuthorizationMode.ENQUIRY_ONLY, max, false, TaskIntake.FORM);
    }

    private static Command valid() {
        return command("Ottawa", TODAY.plusDays(10), TODAY.plusDays(12), 1, 2, 0, "a@example.com",
                NOW.plusSeconds(3600), null);
    }

    private static Command withEmail(String email) {
        return command("Ottawa", TODAY.plusDays(1), TODAY.plusDays(2), 1, 1, 0, email, NOW.plusSeconds(60), null);
    }

    @Test
    void returnsTheResolvedMunicipality() {
        assertThat(validator.validate(valid())).isEqualTo(ottawa());
    }

    @Test
    void trimsTheCityBeforeLookup() {
        Command padded = command("  Ottawa ", TODAY.plusDays(1), TODAY.plusDays(2), 1, 1, 0, "a@example.com",
                NOW.plusSeconds(60), null);
        assertThat(validator.validate(padded).name()).isEqualTo("Ottawa");
    }

    @Test
    void checkInTodayIsAllowed() {
        Command today = command("Ottawa", TODAY, TODAY.plusDays(1), 1, 1, 0, "a@example.com", NOW.plusSeconds(60), null);
        assertThat(validator.validate(today)).isNotNull();
    }

    @Test
    void todayIsTheDateInOntarioNotUtc() {
        // 03:30 UTC on Nov 3 is still Nov 2 in Toronto: a Nov 2 check-in is not in the past.
        Clock lateEvening = Clock.fixed(Instant.parse("2026-11-03T03:30:00Z"), ZoneOffset.UTC);
        BookingRequestValidator v = new BookingRequestValidator(municipalities, geocoder, lateEvening);
        Command tonight = command("Ottawa", LocalDate.parse("2026-11-02"), LocalDate.parse("2026-11-03"), 1, 1, 0,
                "a@example.com", Instant.parse("2026-11-03T05:00:00Z"), null);
        assertThat(v.validate(tonight)).isEqualTo(ottawa());
    }

    @Test
    void collectsEveryErrorAtOnceWithItsFieldAndSkipsTheLookup() {
        Command bad = new Command(" ", " ", null, null, 0, 0, -1, "not-an-email", null, null, null,
                BigDecimal.ZERO, false, TaskIntake.FORM);

        assertThatExceptionOfType(RequestValidationException.class)
                .isThrownBy(() -> validator.validate(bad))
                .satisfies(e -> assertThat(e.errors()).extracting(FieldError::field).containsExactly(
                        "requestId", "city", "checkIn", "checkOut", "rooms", "adults", "children",
                        "destinationEmail", "deadline", "maximumTotalCad"))
                .satisfies(e -> assertThat(e.errors()).allSatisfy(f -> assertThat(f.message()).isNotBlank()))
                .withMessageStartingWith("requestId: Request ID is required; city: City is required");
        verify(municipalities, never()).findByNameOrAlias(anyString());
        verify(geocoder, never()).findOntarioLocality(anyString());
    }

    @Test
    void rejectsAPastCheckInAndAnInvertedStay() {
        Command past = command("Ottawa", TODAY.minusDays(1), TODAY.minusDays(2), 1, 1, 0, "a@example.com",
                NOW.plusSeconds(60), null);
        assertThatExceptionOfType(RequestValidationException.class)
                .isThrownBy(() -> validator.validate(past))
                .satisfies(e -> assertThat(e.errors()).containsExactly(
                        new FieldError("checkIn", "Check-in date is in the past"),
                        new FieldError("checkOut", "Check-out must be after check-in")));
    }

    @Test
    void sameDayCheckOutIsRejected() {
        Command zeroNights = command("Ottawa", TODAY.plusDays(3), TODAY.plusDays(3), 1, 1, 0, "a@example.com",
                NOW.plusSeconds(60), null);
        assertThatExceptionOfType(RequestValidationException.class)
                .isThrownBy(() -> validator.validate(zeroNights))
                .satisfies(e -> assertThat(e.errors()).extracting(FieldError::field).containsExactly("checkOut"));
    }

    @Test
    void deadlineMustBeStrictlyInTheFuture() {
        Command now = command("Ottawa", TODAY.plusDays(1), TODAY.plusDays(2), 1, 1, 0, "a@example.com", NOW, null);
        assertThatExceptionOfType(RequestValidationException.class)
                .isThrownBy(() -> validator.validate(now))
                .satisfies(e -> assertThat(e.errors()).extracting(FieldError::field).containsExactly("deadline"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"plain", "a@b", "a b@example.com", "@example.com", "a@@example.com"})
    void rejectsMalformedEmails(String email) {
        assertThatExceptionOfType(RequestValidationException.class)
                .isThrownBy(() -> validator.validate(withEmail(email)))
                .satisfies(e -> assertThat(e.errors()).extracting(FieldError::field).containsExactly("destinationEmail"));
    }

    @Test
    void positiveBudgetIsAccepted() {
        Command budget = command("Ottawa", TODAY.plusDays(1), TODAY.plusDays(2), 1, 1, 0, "a@example.com",
                NOW.plusSeconds(60), new BigDecimal("0.01"));
        assertThat(validator.validate(budget)).isNotNull();
    }

    @Test
    void unknownCityIsOutOfScopeWithPrefixSuggestions() {
        Municipality seaforth = new Municipality(UUID.randomUUID(), "Seaforth", null, Set.of(), new GeoPoint(43.55, -81.39),
                ZoneId.of("America/Toronto"), null);
        when(municipalities.searchByPrefix("Sea", 5)).thenReturn(List.of(seaforth));
        Command seattle = command("Seattle", TODAY.plusDays(1), TODAY.plusDays(2), 1, 1, 0, "a@example.com",
                NOW.plusSeconds(60), null);

        assertThatExceptionOfType(OutOfScopeLocationException.class)
                .isThrownBy(() -> validator.validate(seattle))
                .withMessageContaining("'Seattle'")
                .satisfies(e -> assertThat(e.suggestions()).containsExactly("Seaforth"));
        verify(geocoder).findOntarioLocality("Seattle");
    }

    @Test
    void cityMissingFromTheTableIsGeocodedAndAdded() {
        GeoPoint centre = new GeoPoint(44.98, -76.13);
        Municipality perth = new Municipality(UUID.randomUUID(), "Perth", null, Set.of(), centre,
                ZoneId.of("America/Toronto"), null);
        when(geocoder.findOntarioLocality("Pertth")).thenReturn(Optional.of(new MunicipalityGeocoder.Locality("Perth", centre)));
        when(municipalities.addGeocoded("Perth", centre)).thenReturn(perth);
        Command typo = command("Pertth", TODAY.plusDays(1), TODAY.plusDays(2), 1, 1, 0, "a@example.com",
                NOW.plusSeconds(60), null);

        assertThat(validator.validate(typo)).isEqualTo(perth);
    }

    @Test
    void ambiguousCityIsOutOfScopeWithTheCandidatesAsSuggestions() {
        Municipality a = new Municipality(UUID.randomUUID(), "Springwater", null, Set.of("springfield"),
                new GeoPoint(44.5, -79.7), ZoneId.of("America/Toronto"), null);
        Municipality b = new Municipality(UUID.randomUUID(), "Springfield", null, Set.of(),
                new GeoPoint(42.8, -80.9), ZoneId.of("America/Toronto"), null);
        when(municipalities.findByNameOrAlias("Springfield")).thenReturn(List.of(a, b));
        Command ambiguous = command("Springfield", TODAY.plusDays(1), TODAY.plusDays(2), 1, 1, 0, "a@example.com",
                NOW.plusSeconds(60), null);

        assertThatExceptionOfType(OutOfScopeLocationException.class)
                .isThrownBy(() -> validator.validate(ambiguous))
                .withMessageContaining("matches several")
                .satisfies(e -> assertThat(e.suggestions()).containsExactly("Springwater", "Springfield"));
        verify(geocoder, never()).findOntarioLocality(anyString());
    }
}
