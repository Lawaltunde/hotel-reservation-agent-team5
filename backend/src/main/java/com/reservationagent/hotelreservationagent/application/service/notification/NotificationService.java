package com.reservationagent.hotelreservationagent.application.service.notification;

import com.reservationagent.hotelreservationagent.application.port.out.EmailDeliveryRepository;
import com.reservationagent.hotelreservationagent.application.port.out.EmailProvider;
import com.reservationagent.hotelreservationagent.application.port.out.PhoneCallRepository;
import com.reservationagent.hotelreservationagent.application.service.recording.CallRecordingService;
import com.reservationagent.hotelreservationagent.domain.call.CallStatus;
import com.reservationagent.hotelreservationagent.domain.call.PhoneCall;
import com.reservationagent.hotelreservationagent.domain.notification.EmailDelivery;
import com.reservationagent.hotelreservationagent.domain.notification.EmailStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final long SEND_TIMEOUT_SECONDS = 20;

    private final ReportComposer composer;
    private final EmailProvider emailProvider;
    private final EmailDeliveryRepository deliveries;
    private final PhoneCallRepository calls;
    private final CallRecordingService recordings;
    private final Clock clock;

    public NotificationService(ReportComposer composer, EmailProvider emailProvider, EmailDeliveryRepository deliveries,
                               PhoneCallRepository calls, CallRecordingService recordings, Clock clock) {
        this.composer = composer;
        this.calls = calls;
        this.recordings = recordings;
        this.emailProvider = emailProvider;
        this.deliveries = deliveries;
        this.clock = clock;
    }

    /** @return true if the provider accepted the message (not proof of delivery) */
    public boolean sendReport(ReportComposer.ReportInput input) {
        EmailProvider.Email email = composer.compose(input, recordings.linkTextFor(reportedCall(input)));
        try {
            String messageId = emailProvider.send(email).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            deliveries.save(new EmailDelivery(UUID.randomUUID(), input.task().id(), email.to(), messageId,
                    EmailStatus.ACCEPTED, clock.instant(), null, null));
            return true;
        } catch (Exception e) {
            log.error("task={} report email failed: {}", input.task().id(), e.getMessage());
            deliveries.save(new EmailDelivery(UUID.randomUUID(), input.task().id(), email.to(), null,
                    EmailStatus.FAILED, clock.instant(), null, null));
            return false;
        }
    }

    /**
     * The call whose recording the report links: the one that produced the selected offer, else the
     * most recent call that reached a conversation.
     */
    private PhoneCall reportedCall(ReportComposer.ReportInput input) {
        List<PhoneCall> taskCalls = calls.findByTask(input.task().id());
        if (input.bestOffer() != null) {
            Optional<PhoneCall> offerCall = taskCalls.stream()
                    .filter(c -> c.id().equals(input.bestOffer().callId()))
                    .findFirst();
            if (offerCall.isPresent()) return offerCall.get();
        }
        return taskCalls.stream()
                .filter(c -> c.conversationId() != null && c.status() == CallStatus.COMPLETED)
                .max(Comparator.comparing(PhoneCall::endedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
    }

    public void updateStatus(String providerMessageId, EmailStatus status) {
        deliveries.findByProviderMessageId(providerMessageId).ifPresent(d -> deliveries.save(new EmailDelivery(
                d.id(), d.taskId(), d.recipient(), d.providerMessageId(), status, d.sentAt(),
                status == EmailStatus.DELIVERED ? clock.instant() : d.deliveredAt(),
                status == EmailStatus.BOUNCED ? clock.instant() : d.bouncedAt())));
    }
}
