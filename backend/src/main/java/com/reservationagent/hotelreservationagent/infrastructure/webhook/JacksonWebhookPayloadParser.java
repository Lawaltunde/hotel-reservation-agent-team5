package com.reservationagent.hotelreservationagent.infrastructure.webhook;

import com.reservationagent.hotelreservationagent.application.port.out.WebhookPayloadParser;
import com.reservationagent.hotelreservationagent.domain.transcript.TranscriptTurn;
import com.reservationagent.hotelreservationagent.domain.webhook.WebhookProvider;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Reads only the fields the application needs from ElevenLabs and Resend webhook payloads. */
@Component
class JacksonWebhookPayloadParser implements WebhookPayloadParser {

    private final ObjectMapper mapper;

    JacksonWebhookPayloadParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ParsedEvent parse(WebhookProvider provider, String rawBody) {
        JsonNode root = mapper.readTree(rawBody);
        String type = root.path("type").asString();
        JsonNode data = root.path("data");
        return switch (provider) {
            case ELEVENLABS -> parseElevenLabs(type, data);
            case RESEND -> new ParsedEvent(type + ":" + data.path("email_id").asString() + ":" + root.path("created_at").asString(),
                    type, Optional.empty(),
                    Optional.of(new EmailStatusChanged(data.path("email_id").asString(), type)));
            case TWILIO -> new ParsedEvent(root.path("CallSid").asString(), "twilio", Optional.empty(), Optional.empty());
        };
    }

    private ParsedEvent parseElevenLabs(String type, JsonNode data) {
        String conversationId = data.path("conversation_id").asString();
        String eventId = type + ":" + conversationId;
        return switch (type) {
            case "post_call_transcription" -> {
                List<TranscriptTurn> turns = new ArrayList<>();
                for (JsonNode turn : data.path("transcript")) {
                    String message = turn.path("message").asString("");
                    if (message.isBlank()) continue;
                    turns.add(new TranscriptTurn(
                            "agent".equals(turn.path("role").asString()) ? TranscriptTurn.Speaker.AGENT : TranscriptTurn.Speaker.HOTEL,
                            message,
                            Duration.ofSeconds(turn.path("time_in_call_secs").asLong(0))));
                }
                boolean succeeded = !"failed".equals(data.path("status").asString());
                yield new ParsedEvent(eventId, type,
                        Optional.of(new CallCompleted(conversationId, turns, succeeded, succeeded ? null : "conversation failed",
                                callEnd(data.path("metadata")))),
                        Optional.empty());
            }
            case "call_initiation_failure" -> new ParsedEvent(eventId, type,
                    Optional.of(new CallCompleted(conversationId, List.of(), false, data.path("failure_reason").asString("initiation failed"), null)),
                    Optional.empty());
            default -> new ParsedEvent(eventId, type, Optional.empty(), Optional.empty());
        };
    }

    /** Call start plus duration from the post-call metadata, or {@code null} if either is missing. */
    private static Instant callEnd(JsonNode metadata) {
        JsonNode start = metadata.path("start_time_unix_secs");
        JsonNode duration = metadata.path("call_duration_secs");
        if (!start.isNumber() || !duration.isNumber()) {
            return null;
        }
        return Instant.ofEpochSecond(start.asLong()).plusSeconds(duration.asLong());
    }
}
