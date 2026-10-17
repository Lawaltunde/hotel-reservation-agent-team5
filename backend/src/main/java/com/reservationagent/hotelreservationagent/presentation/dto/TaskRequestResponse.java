package com.reservationagent.hotelreservationagent.presentation.dto;

import com.reservationagent.hotelreservationagent.domain.task.BookingTask;
import com.reservationagent.hotelreservationagent.domain.task.TaskIntake;
import com.reservationagent.hotelreservationagent.domain.task.TaskIntake.StatedField;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What the user asked for, as understood. A value the user did not state is {@code null} and
 * {@code stated.<field>} is false; {@code askedFor} holds what hotels are actually asked
 * ({@code adults} is {@code null} there when hotels are asked without a guest count).
 * {@code city} is the resolved Ontario municipality; {@code maxBudget} is the all-in CAD limit for
 * the whole stay; {@code originalPrompt} is {@code null} for {@code POST /api/tasks}.
 */
public record TaskRequestResponse(
        String city,
        LocalDate checkIn,
        LocalDate checkOut,
        Integer adults,
        Integer children,
        Integer rooms,
        BigDecimal maxBudget,
        BigDecimal maxBudgetPerNight,
        List<String> amenities,
        String notes,
        String email,
        TaskIntake.EmailSource emailSource,
        String originalPrompt,
        Stated stated,
        AskedFor askedFor,
        List<String> assumptions
) {

    public record Stated(boolean checkIn, boolean checkOut, boolean adults, boolean children, boolean rooms,
                         boolean maxBudget, boolean email) {
    }

    public record AskedFor(LocalDate checkIn, LocalDate checkOut, Integer rooms, Integer adults) {
    }

    static TaskRequestResponse from(BookingTask t, String municipalityName) {
        TaskIntake in = t.intake();
        Stated stated = new Stated(in.isStated(StatedField.CHECK_IN), in.isStated(StatedField.CHECK_OUT),
                in.isStated(StatedField.ADULTS), in.isStated(StatedField.CHILDREN), in.isStated(StatedField.ROOMS),
                in.isStated(StatedField.MAX_BUDGET), in.isStated(StatedField.EMAIL));
        return new TaskRequestResponse(
                municipalityName,
                stated.checkIn() ? t.stay().checkIn() : null,
                stated.checkOut() ? t.stay().checkOut() : null,
                stated.adults() ? t.stay().adults() : null,
                stated.children() ? t.stay().children() : null,
                stated.rooms() ? t.stay().rooms() : null,
                t.maximumTotal() == null ? null : t.maximumTotal().amount(),
                in.maximumPerNight() == null ? null : in.maximumPerNight().amount(),
                t.requiredAmenities().stream().sorted().toList(),
                in.notes(),
                t.destinationEmail(),
                in.emailSource(),
                in.originalPrompt(),
                stated,
                new AskedFor(t.stay().checkIn(), t.stay().checkOut(), t.stay().rooms(),
                        stated.adults() ? t.stay().adults() : null),
                in.assumptions());
    }
}
