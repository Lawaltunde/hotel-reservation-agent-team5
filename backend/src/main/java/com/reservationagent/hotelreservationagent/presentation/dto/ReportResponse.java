package com.reservationagent.hotelreservationagent.presentation.dto;

import com.reservationagent.hotelreservationagent.application.service.notification.SentReport;
import com.reservationagent.hotelreservationagent.domain.notification.EmailDelivery;
import com.reservationagent.hotelreservationagent.domain.notification.EmailStatus;
import java.time.Instant;
import java.util.List;

/**
 * The report email exactly as it was sent ({@code GET /api/tasks/{id}} → {@code report}), plus its
 * delivery status. {@code sentAt} is when the provider accepted it, {@code null} if it has not.
 * {@code fields.price} is a CAD number or {@code null}; it is a quote, never a booking.
 */
public record ReportResponse(
        String subject,
        String from,
        String to,
        List<String> cc,
        Instant sentAt,
        EmailStatus deliveryStatus,
        String bodyText,
        String bodyHtml,
        SentReport.Fields fields,
        long tokensSpent,
        SentReport.Tokens tokens,
        List<SentReport.TranscriptEntry> transcripts
) {

    static ReportResponse from(SentReport report, EmailDelivery delivery) {
        if (report == null) {
            return null;
        }
        EmailStatus status = delivery == null ? EmailStatus.PENDING : delivery.status();
        return new ReportResponse(
                report.subject(),
                report.from(),
                report.to(),
                report.cc() == null ? List.of() : report.cc(),
                delivery == null || status == EmailStatus.FAILED ? null : delivery.sentAt(),
                status,
                report.bodyText(),
                report.bodyHtml(),
                report.fields(),
                report.tokens() == null ? 0 : report.tokens().total(),
                report.tokens(),
                report.transcripts() == null ? List.of() : report.transcripts());
    }
}
