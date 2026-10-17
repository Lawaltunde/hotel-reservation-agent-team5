package com.reservationagent.hotelreservationagent.unit.domain;

import com.reservationagent.hotelreservationagent.domain.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.EnumSet;

import static com.reservationagent.hotelreservationagent.domain.task.TaskStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

class TaskStatusTest {

    @Test
    void terminalStatusesAreExactlyTheEndsOfTheWorkflow() {
        assertThat(Arrays.stream(values()).filter(TaskStatus::isTerminal))
                .containsExactlyInAnyOrder(COMPLETED, INVALID_REQUEST, OUT_OF_SCOPE_LOCATION, EMAIL_FAILED, DEADLINE_EXCEEDED);
    }

    @ParameterizedTest
    @CsvSource({
            "RECEIVED,VALIDATING",
            "VALIDATING,DISCOVERING", "VALIDATING,INVALID_REQUEST", "VALIDATING,OUT_OF_SCOPE_LOCATION",
            "DISCOVERING,RANKING", "DISCOVERING,NO_CANDIDATES",
            "RANKING,CALLING_FOR_QUOTES", "RANKING,NO_CANDIDATES",
            "CALLING_FOR_QUOTES,OFFER_SELECTED", "CALLING_FOR_QUOTES,NO_VALID_OFFER",
            "OFFER_SELECTED,BOOKING", "OFFER_SELECTED,EMAIL_PENDING",
            "BOOKING,VERIFYING", "BOOKING,BOOKING_FAILED", "BOOKING,BOOKING_UNCERTAIN",
            "VERIFYING,EMAIL_PENDING", "VERIFYING,BOOKING_UNCERTAIN",
            "NO_CANDIDATES,EMAIL_PENDING", "NO_VALID_OFFER,EMAIL_PENDING",
            "BOOKING_FAILED,EMAIL_PENDING", "BOOKING_UNCERTAIN,EMAIL_PENDING",
            "EMAIL_PENDING,COMPLETED", "EMAIL_PENDING,EMAIL_FAILED"
    })
    void allowsEveryDocumentedTransition(TaskStatus from, TaskStatus to) {
        assertThat(from.canTransitionTo(to)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "RECEIVED,COMPLETED", "VALIDATING,CALLING_FOR_QUOTES", "DISCOVERING,OFFER_SELECTED",
            "CALLING_FOR_QUOTES,BOOKING", "CALLING_FOR_QUOTES,COMPLETED", "OFFER_SELECTED,COMPLETED",
            "NO_CANDIDATES,COMPLETED", "EMAIL_PENDING,CALLING_FOR_QUOTES", "BOOKING_UNCERTAIN,BOOKING"
    })
    void rejectsShortcutsAndBackwardSteps(TaskStatus from, TaskStatus to) {
        assertThat(from.canTransitionTo(to)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"COMPLETED", "INVALID_REQUEST", "OUT_OF_SCOPE_LOCATION",
            "EMAIL_FAILED", "DEADLINE_EXCEEDED"})
    void terminalStatusesGoNowhere(TaskStatus terminal) {
        assertThat(Arrays.stream(values()).filter(terminal::canTransitionTo)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, mode = EnumSource.Mode.EXCLUDE, names = {"COMPLETED", "INVALID_REQUEST",
            "OUT_OF_SCOPE_LOCATION", "EMAIL_FAILED", "DEADLINE_EXCEEDED"})
    void anyRunningTaskCanHitTheDeadline(TaskStatus running) {
        assertThat(running.canTransitionTo(DEADLINE_EXCEEDED)).isTrue();
    }

    @Test
    void failureStatesStillOweTheUserAnEmail() {
        for (TaskStatus failure : EnumSet.of(NO_CANDIDATES, NO_VALID_OFFER, BOOKING_FAILED, BOOKING_UNCERTAIN)) {
            assertThat(failure.isTerminal()).as(failure.name()).isFalse();
            assertThat(failure.canTransitionTo(EMAIL_PENDING)).as(failure.name()).isTrue();
        }
    }
}
