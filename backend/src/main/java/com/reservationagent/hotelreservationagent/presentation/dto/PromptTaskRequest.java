package com.reservationagent.hotelreservationagent.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/tasks/prompt}: the whole request in the guest's words, including the
 * email address the result goes to.
 */
public record PromptTaskRequest(
        @NotBlank(message = "Describe the stay you want, including your email address")
        @Size(max = PromptTaskRequest.MAX_LENGTH, message = "must be at most " + PromptTaskRequest.MAX_LENGTH + " characters")
        String prompt
) {
    public static final int MAX_LENGTH = 2000;
}
