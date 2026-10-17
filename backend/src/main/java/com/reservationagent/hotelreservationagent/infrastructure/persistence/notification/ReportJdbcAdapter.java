package com.reservationagent.hotelreservationagent.infrastructure.persistence.notification;

import com.reservationagent.hotelreservationagent.application.port.out.ReportRepository;
import com.reservationagent.hotelreservationagent.application.service.notification.SentReport;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.UUID;

/** Stores the sent report as JSON (jsonb), one row per task. */
@Repository
class ReportJdbcAdapter implements ReportRepository {

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    ReportJdbcAdapter(JdbcClient jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public void save(UUID taskId, SentReport report) {
        jdbc.sql("""
                        INSERT INTO task_reports (task_id, report, composed_at) VALUES (:taskId, CAST(:report AS jsonb), :at)
                        ON CONFLICT (task_id) DO UPDATE SET report = EXCLUDED.report, composed_at = EXCLUDED.composed_at
                        """)
                .param("taskId", taskId)
                .param("report", mapper.writeValueAsString(report))
                .param("at", java.sql.Timestamp.from(report.composedAt()))
                .update();
    }

    @Override
    public Optional<SentReport> findByTask(UUID taskId) {
        return jdbc.sql("SELECT report::text FROM task_reports WHERE task_id = :taskId")
                .param("taskId", taskId)
                .query(String.class)
                .optional()
                .map(json -> mapper.readValue(json, SentReport.class));
    }
}
