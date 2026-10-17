package com.reservationagent.hotelreservationagent.unit.application;

import com.reservationagent.hotelreservationagent.application.port.in.SubmitBookingTaskUseCase.Command;
import com.reservationagent.hotelreservationagent.application.port.out.MunicipalityRepository;
import com.reservationagent.hotelreservationagent.application.service.validation.BookingRequestValidator;
import com.reservationagent.hotelreservationagent.application.service.validation.OutOfScopeLocationException;
import com.reservationagent.hotelreservationagent.application.service.validation.RequestValidationException;
import com.reservationagent.hotelreservationagent.domain.geography.GeoPoint;
import com.reservationagent.hotelreservationagent.domain.geography.Municipality;
import com.reservationagent.hotelreservationagent.domain.task.AuthorizationMode;
import com.reservationagent.hotelreservationagent.domain.task.TaskIntake;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.reservationagent.hotelreservationagent.support.Fixtures.CLOCK;
import static com.reservationagent.hotelreservationagent.support.Fixtures.NOW;
import static com.reservationagent.hotelreservationagent.support.Fixtures.ottawa;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingRequestValidatorTest {

    private static final LocalDate TODAY = LocalDate.now(CLOCK);

    private final MunicipalityRepository municipalities = mock(MunicipalityRepository.class);
    private final BookingRequestValidator validator = new BookingRequestValidator(municipalities, name -> Optional.empty(), CLOCK);

    @BeforeEach
    void ottawaExists() {
        when(municipalities.findByNameOrAlias(anyString())).thenReturn(List.of());
        when(municipalities.findByNameOrAlias("Ottawa")).thenReturn(List.of(ottawa()));
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
    void collectsEveryErrorAtOnceAndSkipsTheLookup() {
        Command bad = new Command(" ", " ", null, null, 0, 0, -1, "not-an-email", null, null, null,
                BigDecimal.ZERO, false, TaskIntake.FORM);

        assertThatExceptionOfType(RequestValidationException.class)
                .isThrownBy(() -> validator.validate(bad))
                .satisfies(e -> assertThat(e.errors().stream().map(Object::toString).toList()).containsExactly(
                        "requestId: Request ID is required",
                        "city: City is required",
                        "checkIn: Check-in date is required",
                        "checkOut: Check-out date is required",
                        "rooms: At least 1 room is required",
                        "adults: At least 1 adult is required",
                        "children: Children cannot be negative",
                        "destinationEmail: A valid email address is required",
                        "deadline: Deadline must be in the future",
                        "maximumTotalCad: Budget must be positive"))
                .withMessageContaining("requestId: Request ID is required; city: City is required");
        verify(municipalities, never()).findByNameOrAlias(anyString());
    }

    @Test
    void rejectsAPastCheckInAndAnInvertedStay() {
        Command past = command("Ottawa", TODAY.minusDays(1), TODAY.minusDays(2), 1, 1, 0, "a@example.com",
                NOW.plusSeconds(60), null);
        assertThatExceptionOfType(RequestValidationException.class)
                .isThrownBy(() -> validator.validate(past))
                .satisfies(e -> assertThat(e.errors().stream().map(Object::toString).toList())
                        .containsExactly("checkIn: Check-in date is in the past", "checkOut: Check-out must be after check-in"));
    }

    @Test
    void sameDayCheckOutIsRejected() {
        Command zeroNights = command("Ottawa", TODAY.plusDays(3), TODAY.plusDays(3), 1, 1, 0, "a@example.com",
                NOW.plusSeconds(60), null);
        assertThatExceptionOfType(RequestValidationException.class)
                .isThrownBy(() -> validator.validate(zeroNights))
                .satisfies(e -> assertThat(e.errors().stream().map(Object::toString).toList()).containsExactly("checkOut: Check-out must be after check-in"));
    }

    @Test
    void deadlineMustBeStrictlyInTheFuture() {
        Command now = command("Ottawa", TODAY.plusDays(1), TODAY.plusDays(2), 1, 1, 0, "a@example.com", NOW, null);
        assertThatExceptionOfType(RequestValidationException.class)
                .isThrownBy(() -> validator.validate(now))
                .satisfies(e -> assertThat(e.errors().stream().map(Object::toString).toList()).containsExactly("deadline: Deadline must be in the future"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"plain", "a@b", "a b@example.com", "@example.com", "a@@example.com"})
    void rejectsMalformedEmails(String email) {
        Command bad = command("Ottawa", TODAY.plusDays(1), TODAY.plusDays(2), 1, 1, 0, email, NOW.plusSeconds(60), null);
        assertThatExceptionOfType(RequestValidationException.class)
                .isThrownBy(() -> validator.validate(bad))
                .satisfies(e -> assertThat(e.errors().stream().map(Object::toString).toList()).containsExactly("destinationEmail: A valid email address is required"));
    }

    @Test
    void positiveBudgetIsAccepted() {
        Command budget = command("Ottawa", TODAY.plusDays(1), TODAY.plusDays(2), 1, 1, 0, "a@example.com",
                NOW.plusSeconds(60), new BigDecimal("0.01"));
        assertThat(validator.validate(budget)).isNotNull();
    }

    @Test
    void unknownCityIsOutOfScopeWithNoSuggestions() {
        Command seattle = command("Seattle", TODAY.plusDays(1), TODAY.plusDays(2), 1, 1, 0, "a@example.com",
                NOW.plusSeconds(60), null);
        assertThatExceptionOfType(OutOfScopeLocationException.class)
                .isThrownBy(() -> validator.validate(seattle))
                .withMessageContaining("'Seattle'")
                .satisfies(e -> assertThat(e.suggestions()).isEmpty());
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
    }
}
