package com.reservationagent.hotelreservationagent.application.service.notification;

import com.reservationagent.hotelreservationagent.application.port.out.AiUsageRepository;
import com.reservationagent.hotelreservationagent.application.port.out.EmailDeliveryRepository;
import com.reservationagent.hotelreservationagent.application.port.out.EmailProvider;
import com.reservationagent.hotelreservationagent.application.port.out.HotelIndex;
import com.reservationagent.hotelreservationagent.application.port.out.OfferRepository;
import com.reservationagent.hotelreservationagent.application.port.out.PhoneCallRepository;
import com.reservationagent.hotelreservationagent.application.port.out.ReportRepository;
import com.reservationagent.hotelreservationagent.application.service.notification.ReportComposer.CallEvidence;
import com.reservationagent.hotelreservationagent.application.service.notification.ReportComposer.Composed;
import com.reservationagent.hotelreservationagent.application.service.notification.ReportComposer.ReportContext;
import com.reservationagent.hotelreservationagent.application.service.recording.CallRecordingService;
import com.reservationagent.hotelreservationagent.domain.call.PhoneCall;
import com.reservationagent.hotelreservationagent.domain.notification.EmailDelivery;
import com.reservationagent.hotelreservationagent.domain.notification.EmailStatus;
import com.reservationagent.hotelreservationagent.domain.offer.HotelOffer;
import com.reservationagent.hotelreservationagent.domain.transcript.Transcript;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final long SEND_TIMEOUT_SECONDS = 20;

    private final ReportComposer composer;
    private final EmailProvider emailProvider;
    private final EmailDeliveryRepository deliveries;
    private final PhoneCallRepository calls;
    private final OfferRepository offers;
    private final HotelIndex hotels;
    private final AiUsageRepository usage;
    private final ReportRepository reports;
    private final CallRecordingService recordings;
    private final String conversationUrlTemplate;
    private final Clock clock;

    public NotificationService(ReportComposer composer, EmailProvider emailProvider, EmailDeliveryRepository deliveries,
                               PhoneCallRepository calls, OfferRepository offers, HotelIndex hotels,
                               AiUsageRepository usage, ReportRepository reports, CallRecordingService recordings,
                               @Value("${app.report.conversation-url-template:https://elevenlabs.io/app/conversational-ai/history/{conversationId}}")
                               String conversationUrlTemplate,
                               Clock clock) {
        this.composer = composer;
        this.emailProvider = emailProvider;
        this.deliveries = deliveries;
        this.calls = calls;
        this.offers = offers;
        this.hotels = hotels;
        this.usage = usage;
        this.reports = reports;
        this.recordings = recordings;
        this.conversationUrlTemplate = conversationUrlTemplate;
        this.clock = clock;
    }

    /** @return true if the provider accepted the message (not proof of delivery) */
    public boolean sendReport(ReportComposer.ReportInput input) {
        UUID taskId = input.task().id();
        Composed composed;
        try {
            composed = composer.compose(input, context(input));
        } catch (RuntimeException e) {
            // Never let a report bug cost the email: send the essentials instead.
            log.error("task={} report composition failed, sending the fallback report", taskId, e);
            composed = composer.fallback(input);
        }
        try {
            reports.save(taskId, composed.report());
        } catch (RuntimeException e) {
            log.error("task={} could not store the report snapshot: {}", taskId, e.getMessage());
        }
        EmailProvider.Email email = composed.email();
        String messageId;
        try {
            messageId = send(email);
        } catch (Exception e) {
            if (email.cc().isEmpty() || !(rootCause(e) instanceof EmailProvider.RecipientRejectedException)) {
                return failed(taskId, email, e);
            }
            // Never lose the email: a provider that refuses the copy (e.g. a sandbox key) gets the To alone.
            log.warn("task={} provider rejected the recipients ({}); retrying without cc {}", taskId,
                    rootCause(e).getMessage(), email.cc());
            email = new EmailProvider.Email(email.to(), List.of(), email.subject(), email.textBody(), email.attachments());
            try {
                messageId = send(email);
            } catch (Exception retry) {
                return failed(taskId, email, retry);
            }
            storeWithoutCc(taskId, composed.report());
        }
        // The provider accepted it: the email is sent, whatever happens to our bookkeeping below.
        saveDelivery(new EmailDelivery(UUID.randomUUID(), taskId, email.to(), messageId,
                EmailStatus.ACCEPTED, clock.instant(), null, null));
        return true;
    }

    /** Keeps the stored copy true to what was sent after the cc was dropped. */
    private void storeWithoutCc(UUID taskId, SentReport r) {
        try {
            reports.save(taskId, new SentReport(r.subject(), r.from(), r.to(), List.of(), r.bodyText(), r.bodyHtml(),
                    r.fields(), r.tokens(), r.transcripts(), r.composedAt()));
        } catch (RuntimeException e) {
            log.error("task={} could not update the report snapshot: {}", taskId, e.getMessage());
        }
    }

    private String send(EmailProvider.Email email) throws Exception {
        return emailProvider.send(email).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private boolean failed(UUID taskId, EmailProvider.Email email, Exception e) {
        log.error("task={} report email failed: {}", taskId, rootCause(e).getMessage());
        saveDelivery(new EmailDelivery(UUID.randomUUID(), taskId, email.to(), null,
                EmailStatus.FAILED, clock.instant(), null, null));
        return false;
    }

    private static Throwable rootCause(Throwable e) {
        Throwable t = e;
        while ((t instanceof java.util.concurrent.ExecutionException || t instanceof java.util.concurrent.CompletionException)
                && t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }

    /** Records a delivery, retrying once; a database error never changes the outcome or resends. */
    private void saveDelivery(EmailDelivery delivery) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                deliveries.save(delivery);
                return;
            } catch (RuntimeException e) {
                log.error("task={} could not record the {} email delivery (attempt {}): {}", delivery.taskId(),
                        delivery.status(), attempt, e.getMessage());
            }
        }
    }

    /** The calls in the order they were placed, each with its hotel, offer, transcript and links. */
    private ReportContext context(ReportComposer.ReportInput input) {
        UUID taskId = input.task().id();
        Map<UUID, HotelOffer> offerByCall = offers.findByTask(taskId).stream()
                .collect(Collectors.toMap(HotelOffer::callId, Function.identity(), (a, b) -> b));
        Map<UUID, Transcript> transcriptByCall = input.transcripts().stream()
                .collect(Collectors.toMap(Transcript::callId, Function.identity(), (a, b) -> b));
        List<CallEvidence> evidence = calls.findByTask(taskId).stream()
                .sorted(Comparator.comparing(PhoneCall::startedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(call -> new CallEvidence(call, hotels.findById(call.hotelId()).orElse(null),
                        offerByCall.get(call.id()), transcriptByCall.get(call.id()), recordings.issueFor(call),
                        conversationUrl(call)))
                .toList();
        return new ReportContext(evidence, usage.totals(taskId));
    }

    private String conversationUrl(PhoneCall call) {
        if (call.conversationId() == null || call.conversationId().startsWith("dev-")) {
            return null;
        }
        return conversationUrlTemplate.replace("{conversationId}", call.conversationId());
    }

    public void updateStatus(String providerMessageId, EmailStatus status) {
        deliveries.findByProviderMessageId(providerMessageId).ifPresent(d -> deliveries.save(new EmailDelivery(
                d.id(), d.taskId(), d.recipient(), d.providerMessageId(), status, d.sentAt(),
                status == EmailStatus.DELIVERED ? clock.instant() : d.deliveredAt(),
                status == EmailStatus.BOUNCED ? clock.instant() : d.bouncedAt())));
    }
}
