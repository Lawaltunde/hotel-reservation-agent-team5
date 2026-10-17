package com.reservationagent.hotelreservationagent.infrastructure.client.elevenlabs;

import com.reservationagent.hotelreservationagent.application.port.out.VoiceCallProvider;
import com.reservationagent.hotelreservationagent.domain.hotel.PhoneNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Local-development stand-in: logs instead of dialling. Post a sample payload to
 * /webhooks/elevenlabs to simulate the call result.
 */
@Component
@ConditionalOnProperty(name = "hotel-agent.elevenlabs.enabled", havingValue = "false", matchIfMissing = true)
class LoggingVoiceCallProvider implements VoiceCallProvider {

    private static final Logger log = LoggerFactory.getLogger(LoggingVoiceCallProvider.class);

    @Override
    public CompletableFuture<StartedCall> startCall(PhoneNumber to, Map<String, String> dynamicVariables) {
        String conversationId = "dev-" + UUID.randomUUID();
        log.info("[voice disabled] would call {} conversation={} vars={}", to.e164(), conversationId, dynamicVariables);
        return CompletableFuture.completedFuture(new StartedCall("dev-call-" + UUID.randomUUID(), conversationId));
    }
}
