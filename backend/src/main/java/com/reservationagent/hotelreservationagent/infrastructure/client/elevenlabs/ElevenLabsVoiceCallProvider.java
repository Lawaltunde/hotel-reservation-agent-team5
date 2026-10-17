package com.reservationagent.hotelreservationagent.infrastructure.client.elevenlabs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.reservationagent.hotelreservationagent.application.port.out.VoiceCallProvider;
import com.reservationagent.hotelreservationagent.domain.hotel.PhoneNumber;
import com.reservationagent.hotelreservationagent.infrastructure.config.HotelAgentProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Starts an outbound call through the ElevenLabs native Twilio integration. The agent's prompt is
 * configured in ElevenLabs (see docs/voice-agent-prompt.md); per-call details are dynamic variables.
 */
@Component
@ConditionalOnProperty(name = "hotel-agent.elevenlabs.enabled", havingValue = "true")
class ElevenLabsVoiceCallProvider implements VoiceCallProvider {

    private final WebClient client;
    private final HotelAgentProperties.ElevenLabs config;

    ElevenLabsVoiceCallProvider(@Qualifier("elevenLabs") WebClient client, HotelAgentProperties properties) {
        this.client = client;
        this.config = properties.elevenlabs();
    }

    @Override
    public CompletableFuture<StartedCall> startCall(PhoneNumber to, Map<String, String> dynamicVariables) {
        OutboundCallRequest request = new OutboundCallRequest(config.agentId(), config.agentPhoneNumberId(), to.e164(),
                new ClientData(dynamicVariables));
        return client.post()
                .uri("/v1/convai/twilio/outbound-call")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(OutboundCallResponse.class)
                .map(r -> {
                    if (!r.success()) {
                        throw new IllegalStateException("ElevenLabs refused call: " + r.message());
                    }
                    return new StartedCall(r.callSid(), r.conversationId());
                })
                .toFuture();
    }

    record OutboundCallRequest(
            @JsonProperty("agent_id") String agentId,
            @JsonProperty("agent_phone_number_id") String agentPhoneNumberId,
            @JsonProperty("to_number") String toNumber,
            @JsonProperty("conversation_initiation_client_data") ClientData clientData) {
    }

    record ClientData(@JsonProperty("dynamic_variables") Map<String, String> dynamicVariables) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OutboundCallResponse(
            boolean success,
            String message,
            @JsonProperty("conversation_id") String conversationId,
            @JsonProperty("callSid") String callSid) {
    }
}
