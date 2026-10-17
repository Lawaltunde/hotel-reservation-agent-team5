package com.reservationagent.hotelreservationagent.unit.application;

import com.reservationagent.hotelreservationagent.application.port.in.HandleWebhookUseCase.Result;
import com.reservationagent.hotelreservationagent.application.port.out.WebhookEventRepository;
import com.reservationagent.hotelreservationagent.application.port.out.WebhookPayloadParser;
import com.reservationagent.hotelreservationagent.application.port.out.WebhookPayloadParser.ParsedEvent;
import com.reservationagent.hotelreservationagent.application.port.out.WebhookSignatureVerifier;
import com.reservationagent.hotelreservationagent.application.service.call.CallResultService;
import com.reservationagent.hotelreservationagent.application.service.notification.NotificationService;
import com.reservationagent.hotelreservationagent.application.service.orchestration.TaskOrchestrator;
import com.reservationagent.hotelreservationagent.application.service.webhook.WebhookService;
import com.reservationagent.hotelreservationagent.domain.notification.EmailStatus;
import com.reservationagent.hotelreservationagent.domain.webhook.WebhookEvent;
import com.reservationagent.hotelreservationagent.domain.webhook.WebhookProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.reservationagent.hotelreservationagent.support.Fixtures.CLOCK;
import static com.reservationagent.hotelreservationagent.support.Fixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WebhookServiceTest {

    private final WebhookSignatureVerifier resendVerifier = mock(WebhookSignatureVerifier.class);
    private final WebhookPayloadParser parser = mock(WebhookPayloadParser.class);
    private final WebhookEventRepository events = mock(WebhookEventRepository.class);
    private final CallResultService callResults = mock(CallResultService.class);
    private final TaskOrchestrator orchestrator = mock(TaskOrchestrator.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final WebhookService service = new WebhookService(List.of(resendVerifier), parser, events, callResults,
            orchestrator, notifications, CLOCK);

    private static final Map<String, String> HEADERS = Map.of("svix-id", "msg_1");
    private static final String BODY = "{\"type\":\"email.delivered\"}";

    WebhookServiceTest() {
        when(resendVerifier.supports(WebhookProvider.RESEND)).thenReturn(true);
    }

    private void resendEvent(String type) {
        when(parser.parse(WebhookProvider.RESEND, BODY)).thenReturn(new ParsedEvent("evt-1", type, Optional.empty(),
                Optional.of(new WebhookPayloadParser.EmailStatusChanged("msg-123", type))));
    }

    @Test
    void badSignatureIsRejectedBeforeParsing() {
        when(resendVerifier.verify(HEADERS, BODY)).thenReturn(false);

        assertThat(service.handle(WebhookProvider.RESEND, HEADERS, BODY)).isEqualTo(Result.REJECTED_SIGNATURE);
        verifyNoInteractions(parser, events, notifications, orchestrator);
    }

    @Test
    void providerWithoutAVerifierIsRejected() {
        assertThat(service.handle(WebhookProvider.ELEVENLABS, HEADERS, BODY)).isEqualTo(Result.REJECTED_SIGNATURE);
        verify(resendVerifier, never()).verify(any(), anyString());
        verifyNoInteractions(parser);
    }

    @Test
    void firstDeliveryIsStoredDispatchedAndMarkedProcessed() {
        when(resendVerifier.verify(HEADERS, BODY)).thenReturn(true);
        resendEvent("email.delivered");
        when(events.insertIfAbsent(any())).thenReturn(true);

        assertThat(service.handle(WebhookProvider.RESEND, HEADERS, BODY)).isEqualTo(Result.ACCEPTED);

        ArgumentCaptor<WebhookEvent> stored = ArgumentCaptor.forClass(WebhookEvent.class);
        verify(events).insertIfAbsent(stored.capture());
        assertThat(stored.getValue()).satisfies(e -> {
            assertThat(e.provider()).isEqualTo(WebhookProvider.RESEND);
            assertThat(e.externalEventId()).isEqualTo("evt-1");
            assertThat(e.payload()).isEqualTo(BODY);
            assertThat(e.receivedAt()).isEqualTo(NOW);
            assertThat(e.status()).isEqualTo(WebhookEvent.ProcessingStatus.RECEIVED);
        });
        verify(notifications).updateStatus("msg-123", EmailStatus.DELIVERED);
        verify(events).markProcessed(stored.getValue(), WebhookEvent.ProcessingStatus.PROCESSED);
    }

    @Test
    void redeliveryIsAcknowledgedButNotProcessedTwice() {
        when(resendVerifier.verify(HEADERS, BODY)).thenReturn(true);
        resendEvent("email.delivered");
        when(events.insertIfAbsent(any())).thenReturn(false);

        assertThat(service.handle(WebhookProvider.RESEND, HEADERS, BODY)).isEqualTo(Result.DUPLICATE);
        verifyNoInteractions(notifications, orchestrator, callResults);
        verify(events, never()).markProcessed(any(), any());
    }

    @Test
    void processingFailureIsRecordedAndStillAcknowledged() {
        when(resendVerifier.verify(HEADERS, BODY)).thenReturn(true);
        resendEvent("email.bounced");
        when(events.insertIfAbsent(any())).thenReturn(true);
        org.mockito.Mockito.doThrow(new IllegalStateException("db down"))
                .when(notifications).updateStatus(anyString(), any());

        assertThat(service.handle(WebhookProvider.RESEND, HEADERS, BODY)).isEqualTo(Result.ACCEPTED);
        verify(notifications).updateStatus("msg-123", EmailStatus.BOUNCED);
        verify(events).markProcessed(any(), eq(WebhookEvent.ProcessingStatus.FAILED));
    }

    @Test
    void finishedCallResumesTheOrchestrator() {
        when(resendVerifier.supports(WebhookProvider.ELEVENLABS)).thenReturn(true);
        when(resendVerifier.verify(HEADERS, BODY)).thenReturn(true);
        WebhookPayloadParser.CallCompleted completed =
                new WebhookPayloadParser.CallCompleted("conv-1", List.of(), true, null);
        when(parser.parse(WebhookProvider.ELEVENLABS, BODY)).thenReturn(new ParsedEvent("post_call_transcription:conv-1",
                "post_call_transcription", Optional.of(completed), Optional.empty()));
        when(events.insertIfAbsent(any())).thenReturn(true);
        UUID taskId = UUID.randomUUID();
        when(callResults.record(completed)).thenReturn(Optional.of(taskId));

        assertThat(service.handle(WebhookProvider.ELEVENLABS, HEADERS, BODY)).isEqualTo(Result.ACCEPTED);
        verify(orchestrator).onCallFinished(taskId);
        verifyNoInteractions(notifications);
    }
}
