package com.reservationagent.hotelreservationagent.unit.infrastructure;

import com.reservationagent.hotelreservationagent.domain.webhook.WebhookProvider;
import com.reservationagent.hotelreservationagent.infrastructure.config.HotelAgentProperties;
import com.reservationagent.hotelreservationagent.infrastructure.webhook.ResendSignatureVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static com.reservationagent.hotelreservationagent.support.Fixtures.CLOCK;
import static com.reservationagent.hotelreservationagent.support.Fixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;

/** Svix-style signatures as sent by Resend. */
class ResendSignatureVerifierTest {

    private static final byte[] KEY = "resend-test-signing-key-32-bytes".getBytes(StandardCharsets.UTF_8);
    private static final String SECRET = "whsec_" + Base64.getEncoder().encodeToString(KEY);
    private static final String BODY = "{\"type\":\"email.delivered\",\"data\":{\"email_id\":\"em_1\"}}";
    private static final String ID = "msg_2abc";

    private final long now = NOW.getEpochSecond();
    private final ResendSignatureVerifier verifier = verifier(SECRET);

    static ResendSignatureVerifier verifier(String secret) {
        HotelAgentProperties.Resend config = new HotelAgentProperties.Resend(true, "http://unused", "key",
                "agent@example.com", secret);
        return new ResendSignatureVerifier(new HotelAgentProperties(null, null, null, null, config, null), CLOCK);
    }

    static String sign(byte[] key, String id, long timestamp, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return Base64.getEncoder().encodeToString(
                mac.doFinal((id + "." + timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
    }

    private static Map<String, String> headers(String id, String timestamp, String signature) {
        Map<String, String> h = new HashMap<>();
        if (id != null) h.put("svix-id", id);
        if (timestamp != null) h.put("svix-timestamp", timestamp);
        if (signature != null) h.put("svix-signature", signature);
        return h;
    }

    private Map<String, String> signed(long timestamp) throws Exception {
        return headers(ID, String.valueOf(timestamp), "v1," + sign(KEY, ID, timestamp, BODY));
    }

    @Test
    void supportsOnlyResend() {
        assertThat(verifier.supports(WebhookProvider.RESEND)).isTrue();
        assertThat(verifier.supports(WebhookProvider.ELEVENLABS)).isFalse();
    }

    @Test
    void acceptsAValidSignature() throws Exception {
        assertThat(verifier.verify(signed(now), BODY)).isTrue();
    }

    @Test
    void secretWithoutTheWhsecPrefixAlsoWorks() throws Exception {
        assertThat(verifier(Base64.getEncoder().encodeToString(KEY)).verify(signed(now), BODY)).isTrue();
    }

    @Test
    void acceptsWhenAnyListedSignatureMatches() throws Exception {
        String good = sign(KEY, ID, now, BODY);
        String rotatedOut = sign("old-key".getBytes(StandardCharsets.UTF_8), ID, now, BODY);
        assertThat(verifier.verify(headers(ID, String.valueOf(now), "v1," + rotatedOut + " v1," + good), BODY)).isTrue();
    }

    @Test
    void ignoresSignaturesOfOtherVersions() throws Exception {
        String good = sign(KEY, ID, now, BODY);
        assertThat(verifier.verify(headers(ID, String.valueOf(now), "v2," + good), BODY)).isFalse();
    }

    @Test
    void rejectsATamperedBodyIdOrTimestamp() throws Exception {
        String sig = "v1," + sign(KEY, ID, now, BODY);
        assertThat(verifier.verify(headers(ID, String.valueOf(now), sig), BODY + " ")).isFalse();
        assertThat(verifier.verify(headers("msg_other", String.valueOf(now), sig), BODY)).isFalse();
        assertThat(verifier.verify(headers(ID, String.valueOf(now - 1), sig), BODY)).isFalse();
    }

    @Test
    void timestampToleranceIsFiveMinutesEitherWay() throws Exception {
        assertThat(verifier.verify(signed(now - 300), BODY)).isTrue();
        assertThat(verifier.verify(signed(now + 300), BODY)).isTrue();
        assertThat(verifier.verify(signed(now - 301), BODY)).isFalse();
        assertThat(verifier.verify(signed(now + 301), BODY)).isFalse();
    }

    @Test
    void rejectsMissingHeaders() throws Exception {
        String sig = "v1," + sign(KEY, ID, now, BODY);
        String ts = String.valueOf(now);
        assertThat(verifier.verify(headers(null, ts, sig), BODY)).isFalse();
        assertThat(verifier.verify(headers(ID, null, sig), BODY)).isFalse();
        assertThat(verifier.verify(headers(ID, ts, null), BODY)).isFalse();
    }

    @Test
    void rejectsMalformedValuesWithoutThrowing() {
        String ts = String.valueOf(now);
        assertThat(verifier.verify(headers(ID, "not-a-number", "v1,AAAA"), BODY)).isFalse();
        assertThat(verifier.verify(headers(ID, ts, "v1,not*base64"), BODY)).isFalse();
        assertThat(verifier.verify(headers(ID, ts, "garbage"), BODY)).isFalse();
        assertThat(verifier("whsec_***").verify(headers(ID, ts, "v1,AAAA"), BODY)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void withoutAConfiguredSecretEverythingIsRejected(String secret) throws Exception {
        assertThat(verifier(secret).verify(signed(now), BODY)).isFalse();
    }
}
