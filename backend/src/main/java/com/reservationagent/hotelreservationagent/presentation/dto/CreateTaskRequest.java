package com.reservationagent.hotelreservationagent.presentation.dto;

import com.reservationagent.hotelreservationagent.domain.task.AuthorizationMode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Body of {@code POST /api/tasks}. Structural checks happen here; business rules (dates not in
 * the past, deadline in the future, Ontario-only destination) are enforced by the application layer.
 *
 * @param requestId       client-generated idempotency key; resubmitting returns the same task
 * @param deadline        ISO-8601 instant by which the report email must be sent
 * @param authorizationMode defaults to {@link AuthorizationMode#ENQUIRY_ONLY}
 * @param maximumTotalCad optional all-in budget in CAD
 */
public record CreateTaskRequest(
        @NotBlank @Size(max = 128) String requestId,
        @NotBlank @Size(max = 200) String city,
        @NotNull LocalDate checkIn,
        @NotNull LocalDate checkOut,
        @NotNull @Min(1) Integer rooms,
        @NotNull @Min(1) Integer adults,
        @Min(0) Integer children,
        @NotBlank @Email String destinationEmail,
        @NotNull Instant deadline,
        List<@NotBlank String> requiredAmenities,
        AuthorizationMode authorizationMode,
        @Positive BigDecimal maximumTotalCad,
        Boolean allowCompetingQuoteDisclosure
) {
}
