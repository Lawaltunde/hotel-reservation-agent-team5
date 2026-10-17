package com.reservationagent.hotelreservationagent.infrastructure.webhook;

import com.reservationagent.hotelreservationagent.application.port.out.WebhookPayloadParser.CallCompleted;
import com.reservationagent.hotelreservationagent.application.port.out.WebhookPayloadParser.ParsedEvent;
import com.reservationagent.hotelreservationagent.domain.transcript.TranscriptTurn.Speaker;
import com.reservationagent.hotelreservationagent.domain.webhook.WebhookProvider;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Lives in the adapter's package because the adapter is package-private. */
class JacksonWebhookPayloadParserTest {

    private final JacksonWebhookPayloadParser parser = new JacksonWebhookPayloadParser(JsonMapper.builder().build());

    private CallCompleted call(String body) {
        ParsedEvent event = parser.parse(WebhookProvider.ELEVENLABS, body);
        assertThat(event.callCompleted()).isPresent();
        return event.callCompleted().get();
    }

    private static String postCall(String metadata, String status) {
        return """
                {"type": "post_call_transcription", "event_timestamp": 1793640000,
                 "data": {"conversation_id": "conv_1", "status": "%s",
                   "transcript": [
                     {"role": "agent", "message": "Hi, I'm an AI assistant calling about a rate.", "time_in_call_secs": 0},
                     {"role": "user", "message": "", "time_in_call_secs": 3},
                     {"role": "user", "message": "Sure, which dates?", "time_in_call_secs": 4}],
                   "metadata": %s}}
                """.formatted(status, metadata);
    }

    @Test
    void callEndIsStartPlusDuration() {
        CallCompleted c = call(postCall("{\"start_time_unix_secs\": 1793639800, \"call_duration_secs\": 95}", "done"));
        assertThat(c.endedAt()).isEqualTo(Instant.ofEpochSecond(1793639895));
    }

    @Test
    void callEndIsUnknownWhenStartOrDurationIsMissingOrNotANumber() {
        assertThat(call(postCall("{\"call_duration_secs\": 95}", "done")).endedAt()).isNull();
        assertThat(call(postCall("{\"start_time_unix_secs\": 1793639800}", "done")).endedAt()).isNull();
        assertThat(call(postCall("{\"start_time_unix_secs\": \"1793639800\", \"call_duration_secs\": 95}", "done")).endedAt()).isNull();
        assertThat(call(postCall("{}", "done")).endedAt()).isNull();
        assertThat(call(postCall("null", "done")).endedAt()).isNull();
    }

    @Test
    void transcriptTurnsSkipBlanksAndMapRoles() {
        CallCompleted c = call(postCall("{}", "done"));
        assertThat(c.conversationId()).isEqualTo("conv_1");
        assertThat(c.callSucceeded()).isTrue();
        assertThat(c.failureReason()).isNull();
        assertThat(c.turns()).hasSize(2);
        assertThat(c.turns().get(0).speaker()).isEqualTo(Speaker.AGENT);
        assertThat(c.turns().get(1).speaker()).isEqualTo(Speaker.HOTEL);
        assertThat(c.turns().get(1).offset()).isEqualTo(Duration.ofSeconds(4));
    }

    @Test
    void failedConversationIsReportedAsFailed() {
        CallCompleted c = call(postCall("{}", "failed"));
        assertThat(c.callSucceeded()).isFalse();
        assertThat(c.failureReason()).isEqualTo("conversation failed");
    }

    @Test
    void initiationFailureHasNoTranscriptAndNoCallEnd() {
        CallCompleted c = call("""
                {"type": "call_initiation_failure", "data": {"conversation_id": "conv_2", "failure_reason": "busy"}}
                """);
        assertThat(c.turns()).isEmpty();
        assertThat(c.callSucceeded()).isFalse();
        assertThat(c.failureReason()).isEqualTo("busy");
        assertThat(c.endedAt()).isNull();
    }

    @Test
    void eventIdIsTypeAndConversationSoRedeliveriesDeduplicate() {
        String body = postCall("{}", "done");
        assertThat(parser.parse(WebhookProvider.ELEVENLABS, body).externalEventId())
                .isEqualTo("post_call_transcription:conv_1")
                .isEqualTo(parser.parse(WebhookProvider.ELEVENLABS, body).externalEventId());
    }

    @Test
    void unknownElevenLabsEventsCarryNoCallResult() {
        ParsedEvent e = parser.parse(WebhookProvider.ELEVENLABS, "{\"type\": \"post_call_audio\", \"data\": {\"conversation_id\": \"c\"}}");
        assertThat(e.callCompleted()).isEmpty();
        assertThat(e.emailStatusChanged()).isEmpty();
    }

    @Test
    void resendEventCarriesTheEmailStatus() {
        ParsedEvent e = parser.parse(WebhookProvider.RESEND, """
                {"type": "email.delivered", "created_at": "2026-11-02T17:20:00Z", "data": {"email_id": "em_1"}}
                """);
        assertThat(e.externalEventId()).isEqualTo("email.delivered:em_1:2026-11-02T17:20:00Z");
        assertThat(e.emailStatusChanged()).hasValueSatisfying(s -> {
            assertThat(s.providerMessageId()).isEqualTo("em_1");
            assertThat(s.status()).isEqualTo("email.delivered");
        });
    }
}
