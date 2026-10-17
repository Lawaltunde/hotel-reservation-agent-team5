package com.reservationagent.hotelreservationagent.application.port.out;

import com.reservationagent.hotelreservationagent.domain.hotel.PhoneNumber;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Places an outbound AI voice call (ElevenLabs Agents over Twilio/SIP). One hotel, one objective.
 * The result of the conversation arrives later through the post-call webhook.
 */
public interface VoiceCallProvider {

    CompletableFuture<StartedCall> startCall(PhoneNumber to, Map<String, String> dynamicVariables);

    record StartedCall(String providerCallId, String conversationId) {
    }
}
