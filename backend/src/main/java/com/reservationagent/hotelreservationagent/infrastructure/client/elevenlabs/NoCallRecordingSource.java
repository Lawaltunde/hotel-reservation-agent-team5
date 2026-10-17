package com.reservationagent.hotelreservationagent.infrastructure.client.elevenlabs;

import com.reservationagent.hotelreservationagent.application.port.out.CallRecordingSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Local-development stand-in: with ElevenLabs disabled there are no real calls and no audio. */
@Component
@ConditionalOnProperty(name = "hotel-agent.elevenlabs.enabled", havingValue = "false", matchIfMissing = true)
class NoCallRecordingSource implements CallRecordingSource {

    @Override
    public Optional<CallRecording> fetch(String conversationId) {
        return Optional.empty();
    }

    @Override
    public boolean isEnabled() {
        return false;
    }
}
