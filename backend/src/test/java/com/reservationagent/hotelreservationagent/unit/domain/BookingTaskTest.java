package com.reservationagent.hotelreservationagent.unit.domain;

import com.reservationagent.hotelreservationagent.domain.shared.DomainException;
import com.reservationagent.hotelreservationagent.domain.task.AuthorizationMode;
import com.reservationagent.hotelreservationagent.domain.task.BookingTask;
import com.reservationagent.hotelreservationagent.domain.task.StayDetails;
import com.reservationagent.hotelreservationagent.domain.task.TaskIntake;
import com.reservationagent.hotelreservationagent.domain.task.TaskOutcome;
import com.reservationagent.hotelreservationagent.domain.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static com.reservationagent.hotelreservationagent.support.Fixtures.CHECK_IN;
import static com.reservationagent.hotelreservationagent.support.Fixtures.CHECK_OUT;
import static com.reservationagent.hotelreservationagent.support.Fixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class BookingTaskTest {

    private static BookingTask received(AuthorizationMode mode, Set<String> amenities) {
        return new BookingTask(UUID.randomUUID(), "req-1", UUID.randomUUID(), new StayDetails(CHECK_IN, CHECK_OUT, 1, 2, 0),
                "a@example.com", mode, null, amenities, false, TaskIntake.FORM, NOW.plusSeconds(3600), TaskStatus.RECEIVED, null, NOW, NOW);
    }

    @Test
    void defaultsToEnquiryOnlyAndNoAmenities() {
        BookingTask task = received(null, null);
        assertThat(task.authorizationMode()).isEqualTo(AuthorizationMode.ENQUIRY_ONLY);
        assertThat(task.requiredAmenities()).isEmpty();
    }

    @Test
    void amenitiesAreCopied() {
        Set<String> amenities = new HashSet<>(Set.of("parking"));
        BookingTask task = received(AuthorizationMode.ENQUIRY_ONLY, amenities);
        amenities.add("pool");
        assertThat(task.requiredAmenities()).containsExactly("parking");
    }

    @Test
    void transitionReturnsANewInstanceAndKeepsTheOriginal() {
        BookingTask task = received(AuthorizationMode.ENQUIRY_ONLY, Set.of());
        Instant later = NOW.plusSeconds(5);
        BookingTask next = task.transitionTo(TaskStatus.VALIDATING, later);

        assertThat(next.status()).isEqualTo(TaskStatus.VALIDATING);
        assertThat(next.updatedAt()).isEqualTo(later);
        assertThat(next.createdAt()).isEqualTo(NOW);
        assertThat(next).usingRecursiveComparison().ignoringFields("status", "updatedAt").isEqualTo(task);
        assertThat(task.status()).isEqualTo(TaskStatus.RECEIVED);
    }

    @Test
    void illegalTransitionThrows() {
        BookingTask task = received(AuthorizationMode.ENQUIRY_ONLY, Set.of());
        assertThatExceptionOfType(DomainException.class)
                .isThrownBy(() -> task.transitionTo(TaskStatus.COMPLETED, NOW))
                .withMessageContaining("RECEIVED -> COMPLETED");
    }

    @Test
    void withOutcomeKeepsTheStatus() {
        BookingTask task = received(AuthorizationMode.ENQUIRY_ONLY, Set.of());
        BookingTask done = task.withOutcome(TaskOutcome.NO_ELIGIBLE_HOTELS, NOW.plusSeconds(1));
        assertThat(done.outcome()).isEqualTo(TaskOutcome.NO_ELIGIBLE_HOTELS);
        assertThat(done.status()).isEqualTo(TaskStatus.RECEIVED);
        assertThat(done.updatedAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    void deadlineIsExclusive() {
        BookingTask task = received(AuthorizationMode.ENQUIRY_ONLY, Set.of());
        assertThat(task.isPastDeadline(task.deadline())).isFalse();
        assertThat(task.isPastDeadline(task.deadline().plusMillis(1))).isTrue();
    }

    @Test
    void onlyBookIfWithinLimitsAllowsBooking() {
        assertThat(AuthorizationMode.ENQUIRY_ONLY.allowsBooking()).isFalse();
        assertThat(AuthorizationMode.REQUIRE_APPROVAL.allowsBooking()).isFalse();
        assertThat(AuthorizationMode.BOOK_IF_WITHIN_LIMITS.allowsBooking()).isTrue();
    }
}
