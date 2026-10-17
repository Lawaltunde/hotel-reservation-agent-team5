package com.reservationagent.hotelreservationagent.unit.infrastructure;

import com.reservationagent.hotelreservationagent.domain.webhook.WebhookProvider;
import com.reservationagent.hotelreservationagent.infrastructure.config.HotelAgentProperties;
import com.reservationagent.hotelreservationagent.infrastructure.webhook.ElevenLabsSignatureVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

import static com.reservationagent.hotelreservationagent.support.Fixtures.CLOCK;
import static com.reservationagent.hotelreservationagent.support.Fixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;

class ElevenLabsSignatureVerifierTest {

    private static final String SECRET = "wsec_test_elevenlabs";
    private static final String BODY = "{\"type\":\"post_call_transcription\",\"data\":{\"conversation_id\":\"conv-1\"}}";

    static ElevenLabsSignatureVerifier verifier(String secret) {
        HotelAgentProperties.ElevenLabs config = new HotelAgentProperties.ElevenLabs(true, "http://unused", "key",
                "agent", "phone", secret);
        return new ElevenLabsSignatureVerifier(new HotelAgentProperties(null, null, config, null, null, null), CLOCK);
    }

    static String sign(String secret, long timestamp, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
    }

    private static Map<String, String> header(String value) {
        return Map.of("elevenlabs-signature", value);
    }

    private final ElevenLabsSignatureVerifier verifier = verifier(SECRET);
    private final long now = NOW.getEpochSecond();

    @Test
    void supportsOnlyElevenLabs() {
        assertThat(verifier.supports(WebhookProvider.ELEVENLABS)).isTrue();
        assertThat(verifier.supports(WebhookProvider.RESEND)).isFalse();
        assertThat(verifier.supports(WebhookProvider.TWILIO)).isFalse();
    }

    @Test
    void acceptsAValidSignature() throws Exception {
        assertThat(verifier.verify(header("t=" + now + ",v0=" + sign(SECRET, now, BODY)), BODY)).isTrue();
    }

    @Test
    void acceptsPartsInAnyOrderWithSpacesAndUppercaseHex() throws Exception {
        String sig = sign(SECRET, now, BODY).toUpperCase();
        assertThat(verifier.verify(header(" v0=" + sig + " , t=" + now), BODY)).isTrue();
    }

    @Test
    void rejectsATamperedBody() throws Exception {
        String sig = sign(SECRET, now, BODY);
        assertThat(verifier.verify(header("t=" + now + ",v0=" + sig), BODY.replace("conv-1", "conv-2"))).isFalse();
    }

    @Test
    void rejectsASignatureMadeWithAnotherSecret() throws Exception {
        assertThat(verifier.verify(header("t=" + now + ",v0=" + sign("other", now, BODY)), BODY)).isFalse();
    }

    @Test
    void rejectsAReplayedSignatureUnderANewTimestamp() throws Exception {
        String oldSig = sign(SECRET, now - 10, BODY);
        assertThat(verifier.verify(header("t=" + now + ",v0=" + oldSig), BODY)).isFalse();
    }

    @Test
    void timestampToleranceIsThirtyMinutesEitherWay() throws Exception {
        long limit = 30 * 60;
        for (long t : new long[] {now - limit, now + limit}) {
            assertThat(verifier.verify(header("t=" + t + ",v0=" + sign(SECRET, t, BODY)), BODY)).as("t=%d", t).isTrue();
        }
        for (long t : new long[] {now - limit - 1, now + limit + 1}) {
            assertThat(verifier.verify(header("t=" + t + ",v0=" + sign(SECRET, t, BODY)), BODY)).as("t=%d", t).isFalse();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "garbage", "t=123", "v0=abcd", "t=,v0=", "t=abc,v0=00", "t=1700000000,v0=zz",
            "t=1700000000;v0=00"})
    void rejectsMalformedHeaders(String value) {
        assertThat(verifier.verify(header(value), BODY)).isFalse();
    }

    @Test
    void rejectsSignaturesThatAreNotHexWithAFreshTimestamp() {
        assertThat(verifier.verify(header("t=" + now + ",v0=zz"), BODY)).isFalse();
        assertThat(verifier.verify(header("t=" + now + ",v0=abc"), BODY)).isFalse();
        assertThat(verifier.verify(header("t=" + now + ",v0="), BODY)).isFalse();
    }

    @Test
    void rejectsAMissingHeader() {
        assertThat(verifier.verify(Map.of(), BODY)).isFalse();
        // Header names arrive lower-cased from the controller; anything else is not looked up.
        assertThat(verifier.verify(Map.of("ElevenLabs-Signature", "t=" + now + ",v0=00"), BODY)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void withoutAConfiguredSecretEverythingIsRejected(String secret) throws Exception {
        String sigWithEmptyKey = sign(" ", now, BODY);
        assertThat(verifier(secret).verify(header("t=" + now + ",v0=" + sigWithEmptyKey), BODY)).isFalse();
    }
}
