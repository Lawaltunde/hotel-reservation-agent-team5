package com.reservationagent.hotelreservationagent.application.service.notification;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The report email as composed and handed to the provider, with its key facts in structured form.
 * Stored once per task; delivery status is tracked separately.
 */
public record SentReport(
        String subject,
        String from,
        String to,
        List<String> cc,
        String bodyText,
        String bodyHtml,
        Fields fields,
        Tokens tokens,
        List<TranscriptEntry> transcripts,
        Instant composedAt,
        List<String> attachments
) {

    public SentReport {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

    /**
     * @param hotelPhone        the number that was called
     * @param reservationNumber the hotel's reference number, only if one was given
     * @param price             total for the stay in CAD, or {@code null} if no price is known
     * @param priceNote         what the price includes and how firm it is
     */
    public record Fields(String hotelName, String hotelAddress, String hotelPhone, String reservationNumber,
                         LocalDate checkIn, LocalDate checkOut, BigDecimal price, String currency, String priceNote,
                         String recordingUrl, String conversationUrl) {
    }

    public record Tokens(long openAiPromptTokens, long openAiCompletionTokens, long elevenLabsCallSeconds) {
        public long total() {
            return openAiPromptTokens + openAiCompletionTokens;
        }
    }

    public record TranscriptEntry(String hotelName, String conversationId, String conversationUrl, String recordingUrl,
                                  Instant startedAt, String text) {
    }
}
