package com.reservationagent.hotelreservationagent.application.service.webhook;

import com.reservationagent.hotelreservationagent.application.port.in.HandleWebhookUseCase;
import com.reservationagent.hotelreservationagent.application.port.out.WebhookEventRepository;
import com.reservationagent.hotelreservationagent.application.port.out.WebhookPayloadParser;
import com.reservationagent.hotelreservationagent.application.port.out.WebhookPayloadParser.ParsedEvent;
import com.reservationagent.hotelreservationagent.application.port.out.WebhookSignatureVerifier;
import com.reservationagent.hotelreservationagent.application.service.call.CallResultService;
import com.reservationagent.hotelreservationagent.application.service.notification.NotificationService;
import com.reservationagent.hotelreservationagent.application.service.orchestration.TaskOrchestrator;
import com.reservationagent.hotelreservationagent.domain.notification.EmailStatus;
import com.reservationagent.hotelreservationagent.domain.webhook.WebhookEvent;
import com.reservationagent.hotelreservationagent.domain.webhook.WebhookProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Authenticates, de-duplicates and dispatches provider webhooks. Safe under provider retries. */
@Service
public class WebhookService implements HandleWebhookUseCase {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final List<WebhookSignatureVerifier> verifiers;
    private final WebhookPayloadParser parser;
    private final WebhookEventRepository events;
    private final CallResultService callResults;
    private final TaskOrchestrator orchestrator;
    private final NotificationService notifications;
    private final Clock clock;

    public WebhookService(List<WebhookSignatureVerifier> verifiers, WebhookPayloadParser parser,
                          WebhookEventRepository events, CallResultService callResults, TaskOrchestrator orchestrator,
                          NotificationService notifications, Clock clock) {
        this.verifiers = verifiers;
        this.parser = parser;
        this.events = events;
        this.callResults = callResults;
        this.orchestrator = orchestrator;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Override
    public Result handle(WebhookProvider provider, Map<String, String> headers, String rawBody) {
        boolean authentic = verifiers.stream()
                .filter(v -> v.supports(provider))
                .findFirst()
                .map(v -> v.verify(headers, rawBody))
                .orElse(false);
        if (!authentic) {
            log.warn("Rejected {} webhook with invalid or missing signature", provider);
            return Result.REJECTED_SIGNATURE;
        }

        ParsedEvent parsed = parser.parse(provider, rawBody);
        WebhookEvent event = new WebhookEvent(UUID.randomUUID(), provider, parsed.externalEventId(), parsed.eventType(),
                rawBody, clock.instant(), null, WebhookEvent.ProcessingStatus.RECEIVED);
        if (!events.insertIfAbsent(event)) {
            return Result.DUPLICATE;
        }
        try {
            parsed.callCompleted().flatMap(callResults::record).ifPresent(orchestrator::onCallFinished);
            parsed.emailStatusChanged().ifPresent(e -> notifications.updateStatus(e.providerMessageId(), toEmailStatus(e.status())));
            events.markProcessed(event, WebhookEvent.ProcessingStatus.PROCESSED);
        } catch (RuntimeException e) {
            log.error("Failed to process {} webhook {}", provider, parsed.externalEventId(), e);
            events.markProcessed(event, WebhookEvent.ProcessingStatus.FAILED);
        }
        return Result.ACCEPTED;
    }

    private static EmailStatus toEmailStatus(String providerStatus) {
        return switch (providerStatus) {
            case "email.delivered" -> EmailStatus.DELIVERED;
            case "email.delivery_delayed" -> EmailStatus.DELAYED;
            case "email.bounced", "email.complained" -> EmailStatus.BOUNCED;
            case "email.failed" -> EmailStatus.FAILED;
            default -> EmailStatus.ACCEPTED;
        };
    }
}
