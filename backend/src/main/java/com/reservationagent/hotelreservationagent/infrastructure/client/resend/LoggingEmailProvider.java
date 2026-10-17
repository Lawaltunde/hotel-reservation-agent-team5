package com.reservationagent.hotelreservationagent.infrastructure.client.resend;

import com.reservationagent.hotelreservationagent.application.port.out.EmailProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Local-development stand-in: logs the report instead of sending it. */
@Component
@ConditionalOnProperty(name = "hotel-agent.resend.enabled", havingValue = "false", matchIfMissing = true)
class LoggingEmailProvider implements EmailProvider {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailProvider.class);

    @Override
    public CompletableFuture<String> send(Email email) {
        log.info("[email disabled] cc={} subject='{}' attachments={}\n{}", email.cc(), email.subject(),
                email.attachments().size(), email.textBody());
        return CompletableFuture.completedFuture("dev-" + UUID.randomUUID());
    }
}
