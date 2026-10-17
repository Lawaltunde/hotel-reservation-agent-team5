package com.reservationagent.hotelreservationagent.infrastructure.ai;

import com.reservationagent.hotelreservationagent.application.port.out.AiUsageRepository.TokenUsage;
import org.springframework.ai.chat.model.ChatResponse;

/** Reads token counts from a Spring AI response; missing usage counts as zero. */
final class Usage {

    private Usage() {
    }

    static TokenUsage of(ChatResponse response) {
        if (response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null) {
            return TokenUsage.NONE;
        }
        var usage = response.getMetadata().getUsage();
        return new TokenUsage(usage.getPromptTokens() == null ? 0 : usage.getPromptTokens(),
                usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens());
    }
}
