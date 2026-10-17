package com.reservationagent.hotelreservationagent.infrastructure.webhook;

import com.reservationagent.hotelreservationagent.application.port.out.WebhookSignatureVerifier;
import com.reservationagent.hotelreservationagent.domain.webhook.WebhookProvider;
import com.reservationagent.hotelreservationagent.infrastructure.config.HotelAgentProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * Resend signs webhooks with Svix: HMAC-SHA256 over {@code "<svix-id>.<svix-timestamp>.<body>"}
 * using the base64 part of the {@code whsec_...} secret; {@code svix-signature} lists "v1,<base64>" values.
 */
@Component
public class ResendSignatureVerifier implements WebhookSignatureVerifier {

    private static final Duration TOLERANCE = Duration.ofMinutes(5);

    private final String secret;
    private final Clock clock;

    public ResendSignatureVerifier(HotelAgentProperties properties, Clock clock) {
        this.secret = properties.resend().webhookSecret();
        this.clock = clock;
    }

    @Override
    public boolean supports(WebhookProvider provider) {
        return provider == WebhookProvider.RESEND;
    }

    @Override
    public boolean verify(Map<String, String> headers, String rawBody) {
        String id = headers.get("svix-id");
        String timestamp = headers.get("svix-timestamp");
        String signatures = headers.get("svix-signature");
        if (secret == null || secret.isBlank() || id == null || timestamp == null || signatures == null) {
            return false;
        }
        try {
            if (Math.abs(clock.instant().getEpochSecond() - Long.parseLong(timestamp)) > TOLERANCE.toSeconds()) {
                return false;
            }
            byte[] key = Base64.getDecoder().decode(secret.startsWith("whsec_") ? secret.substring(6) : secret);
            byte[] expected = HmacSupport.hmacSha256(key, id + "." + timestamp + "." + rawBody);
            for (String candidate : signatures.split(" ")) {
                String[] parts = candidate.split(",", 2);
                if (parts.length == 2 && parts[0].equals("v1")
                        && HmacSupport.equal(expected, Base64.getDecoder().decode(parts[1]))) {
                    return true;
                }
            }
            return false;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
