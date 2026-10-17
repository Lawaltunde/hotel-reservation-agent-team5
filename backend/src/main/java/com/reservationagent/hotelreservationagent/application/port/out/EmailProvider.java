package com.reservationagent.hotelreservationagent.application.port.out;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Transactional email (Resend). Acceptance is not delivery. */
public interface EmailProvider {

    CompletableFuture<String> send(Email email);

    record Email(String to, String subject, String textBody, List<Attachment> attachments) {
    }

    record Attachment(String filename, String contentType, byte[] content) {
    }
}
