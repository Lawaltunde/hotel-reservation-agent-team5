package com.reservationagent.hotelreservationagent.application.port.in;

import com.reservationagent.hotelreservationagent.application.port.out.CallRecordingSource.CallRecording;

import java.util.Optional;

/** Serves a call recording to whoever holds its emailed link. */
public interface OpenCallRecordingUseCase {

    /** @return the audio, or empty if the token is unknown or expired or the recording is gone */
    Optional<CallRecording> open(String token);
}
