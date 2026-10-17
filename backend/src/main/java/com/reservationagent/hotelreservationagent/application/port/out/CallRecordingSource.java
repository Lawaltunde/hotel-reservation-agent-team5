package com.reservationagent.hotelreservationagent.application.port.out;

import java.util.Optional;

/** Fetches a finished call's audio from the voice provider (ElevenLabs), which needs our API key. */
public interface CallRecordingSource {

    /**
     * @return the audio, or empty if the provider has no recording for this conversation
     * @throws RecordingUnavailableException if the provider could not be reached or failed
     */
    Optional<CallRecording> fetch(String conversationId);

    record CallRecording(String contentType, byte[] audio) {
    }

    class RecordingUnavailableException extends RuntimeException {
        public RecordingUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
