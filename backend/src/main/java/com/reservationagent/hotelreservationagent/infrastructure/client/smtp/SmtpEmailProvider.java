package com.reservationagent.hotelreservationagent.infrastructure.client.smtp;

import com.reservationagent.hotelreservationagent.application.port.out.EmailProvider;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Sends the report over SMTP (e.g. Gmail: smtp.gmail.com:587, STARTTLS, an app password), for when no
 * verified Resend domain is available. Selected with {@code app.email.transport=smtp}; the server
 * settings are Spring Boot's {@code spring.mail.*}, fed from {@code SMTP_*}. Acceptance by the SMTP
 * server is not delivery.
 */
@Component
@ConditionalOnProperty(name = "app.email.transport", havingValue = "smtp")
class SmtpEmailProvider implements EmailProvider {

    private final JavaMailSender mailSender;
    private final String from;
    private final Executor executor = Executors.newVirtualThreadPerTaskExecutor();

    SmtpEmailProvider(JavaMailSender mailSender, @Value("${app.email.smtp-from:}") String from) {
        this.mailSender = mailSender;
        if (from == null || from.isBlank()) {
            throw new IllegalStateException("app.email.transport=smtp needs SMTP_FROM (the sender address)");
        }
        this.from = from.strip();
    }

    @Override
    public CompletableFuture<String> send(Email email) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, !email.attachments().isEmpty(),
                        StandardCharsets.UTF_8.name());
                helper.setFrom(from);
                helper.setTo(email.to());
                if (!email.cc().isEmpty()) {
                    helper.setCc(email.cc().toArray(String[]::new));
                }
                helper.setSubject(email.subject());
                helper.setText(email.textBody(), false);
                for (Attachment a : email.attachments()) {
                    helper.addAttachment(a.filename(), new ByteArrayResource(a.content()), a.contentType());
                }
                mailSender.send(message);
                String id = message.getMessageID();
                return id == null ? "smtp-" + System.currentTimeMillis() : id;
            } catch (MessagingException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }
}
