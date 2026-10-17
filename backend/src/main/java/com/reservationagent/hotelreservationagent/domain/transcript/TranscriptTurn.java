package com.reservationagent.hotelreservationagent.domain.transcript;

import java.time.Duration;

/** One utterance. {@code speaker} is AGENT or HOTEL. */
public record TranscriptTurn(Speaker speaker, String text, Duration offset) {

    public enum Speaker { AGENT, HOTEL }
}
