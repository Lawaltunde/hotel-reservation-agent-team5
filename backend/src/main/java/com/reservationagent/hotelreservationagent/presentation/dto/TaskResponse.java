package com.reservationagent.hotelreservationagent.presentation.dto;

import com.reservationagent.hotelreservationagent.application.port.in.GetBookingTaskUseCase.TaskSnapshot;
import com.reservationagent.hotelreservationagent.domain.task.AuthorizationMode;
import com.reservationagent.hotelreservationagent.domain.task.BookingTask;
import com.reservationagent.hotelreservationagent.domain.task.TaskOutcome;
import com.reservationagent.hotelreservationagent.domain.task.TaskStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Body of {@code GET /api/tasks/{id}}. Unknown values are {@code null}. The top-level stay fields are
 * kept for existing clients; {@code request} is the full request as understood.
 */
public record TaskResponse(
        UUID taskId,
        TaskStatus status,
        TaskOutcome outcome,
        String city,
        LocalDate checkIn,
        LocalDate checkOut,
        int rooms,
        int adults,
        int children,
        AuthorizationMode authorizationMode,
        Instant deadline,
        TaskRequestResponse request,
        int candidatesScreened,
        int callsPlaced,
        OfferResponse bestOffer,
        List<OfferResponse> offers,
        Instant createdAt,
        Instant updatedAt
) {

    public static TaskResponse from(TaskSnapshot snapshot) {
        BookingTask t = snapshot.task();
        return new TaskResponse(
                t.id(),
                t.status(),
                t.outcome(),
                snapshot.municipalityName(),
                t.stay().checkIn(),
                t.stay().checkOut(),
                t.stay().rooms(),
                t.stay().adults(),
                t.stay().children(),
                t.authorizationMode(),
                t.deadline(),
                TaskRequestResponse.from(t, snapshot.municipalityName()),
                snapshot.candidatesScreened(),
                snapshot.callsPlaced(),
                snapshot.bestOffer() == null ? null
                        : OfferResponse.from(snapshot.bestOffer(), snapshot.hotels().get(snapshot.bestOffer().hotelId())),
                snapshot.offers().stream()
                        .map(o -> OfferResponse.from(o, snapshot.hotels().get(o.hotelId())))
                        .toList(),
                t.createdAt(),
                t.updatedAt());
    }
}
