package com.reservationagent.hotelreservationagent.application.port.out;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Transactional email (Resend). Acceptance is not delivery. */
public interface EmailProvider {

    CompletableFuture<String> send(Email email);

    /** @param cc copies, possibly empty */
    record Email(String to, List<String> cc, String subject, String textBody, List<Attachment> attachments) {

        public Email {
            cc = cc == null ? List.of() : List.copyOf(cc);
        }
    }

    record Attachment(String filename, String contentType, byte[] content) {
    }

    /**
     * The provider refused the message because of a recipient or sender permission (e.g. a Resend
     * sandbox key that may only send to the account owner), not a transient failure.
     */
    class RecipientRejectedException extends RuntimeException {
        public RecipientRejectedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
