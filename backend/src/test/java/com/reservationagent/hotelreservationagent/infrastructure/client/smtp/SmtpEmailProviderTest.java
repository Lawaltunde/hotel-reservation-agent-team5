package com.reservationagent.hotelreservationagent.infrastructure.client.smtp;

import com.reservationagent.hotelreservationagent.application.port.out.EmailProvider.Attachment;
import com.reservationagent.hotelreservationagent.application.port.out.EmailProvider.Email;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/** The SMTP transport builds the same email the Resend transport sends: to, cc, subject, text, attachments. */
class SmtpEmailProviderTest {

    /** Captures messages instead of talking to a server. */
    static class CapturingSender extends JavaMailSenderImpl {
        final List<MimeMessage> sent = new ArrayList<>();
        RuntimeException failure;

        @Override
        public void send(MimeMessage message) {
            if (failure != null) throw failure;
            try {
                message.saveChanges(); // assigns the Message-ID, as a real send would
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            sent.add(message);
        }
    }

    private final CapturingSender sender = new CapturingSender();
    private final SmtpEmailProvider provider = new SmtpEmailProvider(sender, "Team 5 Agent <team5@example.com>");

    @Test
    void sendsPlainTextWithCc() throws Exception {
        String id = provider.send(new Email("traveller@example.com", List.of("organizer@example.org"),
                "Team # 5 - Hotel Reservation Confirmed!", "Hotel Name: Rideau Inn", List.of())).get(5, TimeUnit.SECONDS);

        MimeMessage m = sender.sent.getFirst();
        assertThat(id).isNotBlank();
        assertThat(m.getFrom()[0].toString()).isEqualTo("Team 5 Agent <team5@example.com>");
        assertThat(m.getRecipients(Message.RecipientType.TO)[0].toString()).isEqualTo("traveller@example.com");
        assertThat(m.getRecipients(Message.RecipientType.CC)[0].toString()).isEqualTo("organizer@example.org");
        assertThat(m.getSubject()).isEqualTo("Team # 5 - Hotel Reservation Confirmed!");
        assertThat(m.getContent().toString()).contains("Hotel Name: Rideau Inn");
    }

    @Test
    void addsAttachments() throws Exception {
        provider.send(new Email("traveller@example.com", List.of(), "s", "body",
                List.of(new Attachment("transcript.txt", "text/plain", "Agent: hi".getBytes(StandardCharsets.UTF_8)))))
                .get(5, TimeUnit.SECONDS);

        MimeMessage m = sender.sent.getFirst();
        assertThat(m.getRecipients(Message.RecipientType.CC)).isNull();
        Multipart parts = (Multipart) m.getContent();
        assertThat(parts.getCount()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void aServerErrorFailsTheFuture() {
        sender.failure = new MailSendException("535 Authentication failed");
        assertThatExceptionOfType(ExecutionException.class)
                .isThrownBy(() -> provider.send(new Email("a@example.com", List.of(), "s", "t", List.of()))
                        .get(5, TimeUnit.SECONDS));
    }

    @Test
    void aSenderAddressIsRequired() {
        assertThatIllegalStateException().isThrownBy(() -> new SmtpEmailProvider(sender, " "));
    }
}
