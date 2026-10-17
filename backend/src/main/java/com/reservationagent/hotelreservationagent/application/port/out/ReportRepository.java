package com.reservationagent.hotelreservationagent.application.port.out;

import com.reservationagent.hotelreservationagent.application.service.notification.SentReport;

import java.util.Optional;
import java.util.UUID;

/** Keeps exactly what was emailed, so the UI shows the sent report rather than a re-render. */
public interface ReportRepository {

    void save(UUID taskId, SentReport report);

    Optional<SentReport> findByTask(UUID taskId);
}
