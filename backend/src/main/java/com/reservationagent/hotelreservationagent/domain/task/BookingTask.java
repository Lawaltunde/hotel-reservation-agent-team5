package com.reservationagent.hotelreservationagent.domain.task;

import com.reservationagent.hotelreservationagent.domain.shared.DomainException;
import com.reservationagent.hotelreservationagent.domain.shared.Money;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * One user request, from submission to final email. Immutable: state changes return a new instance.
 *
 * @param requestId client-generated idempotency key
 */
public record BookingTask(
        UUID id,
        String requestId,
        UUID municipalityId,
        StayDetails stay,
        String destinationEmail,
        AuthorizationMode authorizationMode,
        Money maximumTotal,
        Set<String> requiredAmenities,
        boolean allowCompetingQuoteDisclosure,
        Instant deadline,
        TaskStatus status,
        TaskOutcome outcome,
        Instant createdAt,
        Instant updatedAt
) {

    public BookingTask {
        requiredAmenities = requiredAmenities == null ? Set.of() : Set.copyOf(requiredAmenities);
        authorizationMode = authorizationMode == null ? AuthorizationMode.ENQUIRY_ONLY : authorizationMode;
    }

    public BookingTask transitionTo(TaskStatus target, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new DomainException("Illegal transition " + status + " -> " + target);
        }
        return new BookingTask(id, requestId, municipalityId, stay, destinationEmail, authorizationMode,
                maximumTotal, requiredAmenities, allowCompetingQuoteDisclosure, deadline, target, outcome,
                createdAt, now);
    }

    public BookingTask withOutcome(TaskOutcome newOutcome, Instant now) {
        return new BookingTask(id, requestId, municipalityId, stay, destinationEmail, authorizationMode,
                maximumTotal, requiredAmenities, allowCompetingQuoteDisclosure, deadline, status, newOutcome,
                createdAt, now);
    }

    public boolean isPastDeadline(Instant now) {
        return now.isAfter(deadline);
    }
}
