package com.reservationagent.hotelreservationagent.presentation.dto;

import com.reservationagent.hotelreservationagent.domain.task.BookingTask;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What the task is searching for, after defaults were applied: the "what we understood" view of a
 * prompt. {@code city} is the resolved Ontario municipality; {@code maxBudget} is the all-in CAD
 * limit for the whole stay, or {@code null} for none; {@code originalPrompt} is {@code null} when the
 * task came from {@code POST /api/tasks}.
 */
public record TaskRequestResponse(
        String city,
        LocalDate checkIn,
        LocalDate checkOut,
        int adults,
        int children,
        int rooms,
        BigDecimal maxBudget,
        List<String> amenities,
        String email,
        String originalPrompt
) {

    static TaskRequestResponse from(BookingTask t, String municipalityName) {
        return new TaskRequestResponse(
                municipalityName,
                t.stay().checkIn(),
                t.stay().checkOut(),
                t.stay().adults(),
                t.stay().children(),
                t.stay().rooms(),
                t.maximumTotal() == null ? null : t.maximumTotal().amount(),
                t.requiredAmenities().stream().sorted().toList(),
                t.destinationEmail(),
                t.originalPrompt());
    }
}
