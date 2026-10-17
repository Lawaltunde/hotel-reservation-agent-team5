package com.reservationagent.hotelreservationagent.infrastructure.webhook;

import com.reservationagent.hotelreservationagent.application.port.out.WebhookSignatureVerifier;
import com.reservationagent.hotelreservationagent.domain.webhook.WebhookProvider;
import com.reservationagent.hotelreservationagent.infrastructure.config.HotelAgentProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;

/**
 * Verifies the {@code ElevenLabs-Signature: t=<unix>,v0=<hex hmac>} header, where the HMAC-SHA256
 * is computed over {@code "<t>.<raw body>"} with the webhook secret. Old timestamps are rejected.
 */
@Component
public class ElevenLabsSignatureVerifier implements WebhookSignatureVerifier {

    static final Duration TOLERANCE = Duration.ofMinutes(30);

    private final String secret;
    private final Clock clock;

    public ElevenLabsSignatureVerifier(HotelAgentProperties properties, Clock clock) {
        this.secret = properties.elevenlabs().webhookSecret();
        this.clock = clock;
    }

    @Override
    public boolean supports(WebhookProvider provider) {
        return provider == WebhookProvider.ELEVENLABS;
    }

    @Override
    public boolean verify(Map<String, String> headers, String rawBody) {
        String header = headers.get("elevenlabs-signature");
        if (secret == null || secret.isBlank() || header == null) {
            return false;
        }
        String timestamp = null;
        String signature = null;
        for (String part : header.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length != 2) continue;
            if (kv[0].equals("t")) timestamp = kv[1];
            if (kv[0].equals("v0")) signature = kv[1];
        }
        if (timestamp == null || signature == null) {
            return false;
        }
        try {
            long age = clock.instant().getEpochSecond() - Long.parseLong(timestamp);
            if (Math.abs(age) > TOLERANCE.toSeconds()) {
                return false;
            }
            byte[] expected = HmacSupport.hmacSha256(secret.getBytes(StandardCharsets.UTF_8), timestamp + "." + rawBody);
            return HmacSupport.equal(expected, HexFormat.of().parseHex(signature));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
