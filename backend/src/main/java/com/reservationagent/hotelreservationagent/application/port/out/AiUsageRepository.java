package com.reservationagent.hotelreservationagent.application.port.out;

import java.util.UUID;

/** Per-task metering of model tokens and call time, reported as evidence in the email. */
public interface AiUsageRepository {

    void record(UUID taskId, String purpose, TokenUsage tokens, long callSeconds);

    Totals totals(UUID taskId);

    /** Tokens of one model call; zero when the provider did not report usage. */
    record TokenUsage(long promptTokens, long completionTokens) {
        public static final TokenUsage NONE = new TokenUsage(0, 0);

        public long total() {
            return promptTokens + completionTokens;
        }
    }

    record Totals(long promptTokens, long completionTokens, long callSeconds) {
        public long tokens() {
            return promptTokens + completionTokens;
        }
    }
}
