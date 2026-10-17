package com.reservationagent.hotelreservationagent.infrastructure.persistence.usage;

import com.reservationagent.hotelreservationagent.application.port.out.AiUsageRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;

@Repository
class AiUsageJdbcAdapter implements AiUsageRepository {

    private final JdbcClient jdbc;
    private final Clock clock;

    AiUsageJdbcAdapter(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public void record(UUID taskId, String purpose, TokenUsage tokens, long callSeconds) {
        jdbc.sql("""
                        INSERT INTO ai_usage (id, task_id, purpose, prompt_tokens, completion_tokens, call_seconds, created_at)
                        VALUES (:id, :taskId, :purpose, :prompt, :completion, :seconds, :now)
                        """)
                .param("id", UUID.randomUUID()).param("taskId", taskId).param("purpose", purpose)
                .param("prompt", tokens.promptTokens()).param("completion", tokens.completionTokens())
                .param("seconds", callSeconds).param("now", Timestamp.from(clock.instant()))
                .update();
    }

    @Override
    public Totals totals(UUID taskId) {
        return jdbc.sql("""
                        SELECT COALESCE(SUM(prompt_tokens), 0) AS p, COALESCE(SUM(completion_tokens), 0) AS c,
                               COALESCE(SUM(call_seconds), 0) AS s
                        FROM ai_usage WHERE task_id = :taskId
                        """)
                .param("taskId", taskId)
                .query((rs, i) -> new Totals(rs.getLong("p"), rs.getLong("c"), rs.getLong("s")))
                .single();
    }
}
