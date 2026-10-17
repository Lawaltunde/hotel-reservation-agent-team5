package com.reservationagent.hotelreservationagent.unit.application;

import com.reservationagent.hotelreservationagent.application.port.out.AiUsageRepository;
import com.reservationagent.hotelreservationagent.application.port.out.EmailDeliveryRepository;
import com.reservationagent.hotelreservationagent.application.port.out.EmailProvider;
import com.reservationagent.hotelreservationagent.application.port.out.EmailProvider.Email;
import com.reservationagent.hotelreservationagent.application.port.out.HotelIndex;
import com.reservationagent.hotelreservationagent.application.port.out.OfferRepository;
import com.reservationagent.hotelreservationagent.application.port.out.PhoneCallRepository;
import com.reservationagent.hotelreservationagent.application.port.out.ReportRepository;
import com.reservationagent.hotelreservationagent.application.service.notification.NotificationService;
import com.reservationagent.hotelreservationagent.application.service.notification.ReportComposer;
import com.reservationagent.hotelreservationagent.application.service.notification.ReportComposer.ReportInput;
import com.reservationagent.hotelreservationagent.application.service.pricing.OfferEvaluator;
import com.reservationagent.hotelreservationagent.application.service.recording.CallRecordingService;
import com.reservationagent.hotelreservationagent.domain.notification.EmailDelivery;
import com.reservationagent.hotelreservationagent.domain.notification.EmailStatus;
import com.reservationagent.hotelreservationagent.domain.task.BookingTask;
import com.reservationagent.hotelreservationagent.domain.task.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.reservationagent.hotelreservationagent.support.Fixtures.CLOCK;
import static com.reservationagent.hotelreservationagent.support.Fixtures.task;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The report email is never lost to a recipient the provider refuses, and an accepted email stays sent. */
class NotificationServiceTest {

    private final EmailProvider provider = mock(EmailProvider.class);
    private final EmailDeliveryRepository deliveries = mock(EmailDeliveryRepository.class);
    private final AiUsageRepository usage = mock(AiUsageRepository.class);
    private final NotificationService service;
    private final BookingTask task = task();

    NotificationServiceTest() {
        when(usage.totals(any())).thenReturn(new AiUsageRepository.Totals(0, 0, 0));
        ReportComposer composer = new ReportComposer("confirmed", "unconfirmed", "organizer@example.org", null,
                new OfferEvaluator(CLOCK, new BigDecimal("0.06")), CLOCK);
        CallRecordingService recordings = new CallRecordingService(mock(com.reservationagent.hotelreservationagent
                .application.port.out.RecordingLinkRepository.class), mock(com.reservationagent.hotelreservationagent
                .application.port.out.CallRecordingSource.class), CLOCK, "", java.time.Duration.ofDays(30));
        service = new NotificationService(composer, provider, deliveries, mock(PhoneCallRepository.class),
                mock(OfferRepository.class), mock(HotelIndex.class), usage, mock(ReportRepository.class), recordings,
                "https://elevenlabs.io/app/agents/history/{conversationId}", CLOCK,
                mock(com.reservationagent.hotelreservationagent.application.port.out.CallRecordingSource.class),
                java.time.Duration.ofMillis(100));
    }

    private ReportInput input() {
        return new ReportInput(task, "Ottawa", TaskOutcome.NO_ELIGIBLE_HOTELS, null, null, null, List.of(), 0, 0);
    }

    @Test
    void aRejectedCopyIsRetriedWithTheRecipientAlone() {
        when(provider.send(any()))
                .thenReturn(CompletableFuture.failedFuture(new EmailProvider.RecipientRejectedException("403 sandbox", null)))
                .thenReturn(CompletableFuture.completedFuture("msg-1"));

        assertThat(service.sendReport(input())).isTrue();

        ArgumentCaptor<Email> sent = ArgumentCaptor.forClass(Email.class);
        verify(provider, times(2)).send(sent.capture());
        assertThat(sent.getAllValues().get(0).cc()).containsExactly("organizer@example.org");
        assertThat(sent.getAllValues().get(1).cc()).isEmpty();
        assertThat(sent.getAllValues().get(1).to()).isEqualTo("traveller@example.com");
    }

    @Test
    void anOrdinaryFailureIsNotRetried() {
        when(provider.send(any())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("500")));

        assertThat(service.sendReport(input())).isFalse();
        verify(provider, times(1)).send(any());
    }

    @Test
    void anAcceptedEmailStaysSentWhenTheDeliveryRowCannotBeSaved() {
        when(provider.send(any())).thenReturn(CompletableFuture.completedFuture("msg-1"));
        when(deliveries.save(any())).thenThrow(new IllegalStateException("db down"));

        assertThat(service.sendReport(input())).isTrue();
        ArgumentCaptor<EmailDelivery> saved = ArgumentCaptor.forClass(EmailDelivery.class);
        verify(deliveries, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).allMatch(d -> d.status() == EmailStatus.ACCEPTED);
        verify(provider, times(1)).send(any());
    }
}
