package com.reservationagent.hotelreservationagent.infrastructure.client.elevenlabs;

import com.reservationagent.hotelreservationagent.application.port.out.CallRecordingSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Optional;

/**
 * Downloads a conversation's audio: {@code GET /v1/convai/conversations/{id}/audio} with our API key.
 * The audio is held in memory only while it is passed on to the caller; nothing is stored.
 */
@Component
@ConditionalOnProperty(name = "hotel-agent.elevenlabs.enabled", havingValue = "true")
class ElevenLabsCallRecordingSource implements CallRecordingSource {

    /** A 4-minute call is a few MB of MP3; this leaves ample room. */
    private static final int MAX_AUDIO_BYTES = 32 * 1024 * 1024;
    /** Kept below the report's audio wait so one slow attempt cannot use it all. */
    private static final Duration TIMEOUT = Duration.ofSeconds(12);

    private final WebClient client;

    ElevenLabsCallRecordingSource(@Qualifier("elevenLabs") WebClient client) {
        this.client = client.mutate()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(MAX_AUDIO_BYTES))
                .build();
    }

    @Override
    public Optional<CallRecording> fetch(String conversationId) {
        try {
            return client.get()
                    .uri("/v1/convai/conversations/{id}/audio", conversationId)
                    .accept(MediaType.ALL)
                    .retrieve()
                    .toEntity(byte[].class)
                    .map(response -> {
                        MediaType type = response.getHeaders().getContentType();
                        byte[] body = response.getBody();
                        return body == null || body.length == 0 ? Optional.<CallRecording>empty()
                                : Optional.of(new CallRecording(type == null ? "audio/mpeg" : type.toString(), body));
                    })
                    .block(TIMEOUT);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                return Optional.empty();
            }
            throw new RecordingUnavailableException("ElevenLabs audio request failed: " + e.getStatusCode(), e);
        } catch (RuntimeException e) {
            throw new RecordingUnavailableException("ElevenLabs audio request failed: " + e.getMessage(), e);
        }
    }
}
