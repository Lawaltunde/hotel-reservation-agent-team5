package com.reservationagent.hotelreservationagent.application.service.recording;

import com.reservationagent.hotelreservationagent.application.port.in.OpenCallRecordingUseCase;
import com.reservationagent.hotelreservationagent.application.port.out.CallRecordingSource;
import com.reservationagent.hotelreservationagent.application.port.out.CallRecordingSource.CallRecording;
import com.reservationagent.hotelreservationagent.application.port.out.RecordingLinkRepository;
import com.reservationagent.hotelreservationagent.domain.call.PhoneCall;
import com.reservationagent.hotelreservationagent.domain.call.RecordingLink;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Issues and redeems links to call recordings. ElevenLabs serves audio only with our API key, so
 * the report links to {@code {app.public-base-url}/api/recordings/{token}} and this service streams
 * the audio through. The token is 256 random bits; the conversation ID never appears in the URL.
 */
@Service
public class CallRecordingService implements OpenCallRecordingUseCase {

    public static final String PATH = "/api/recordings/";
    private static final int TOKEN_BYTES = 32;
    /** Base64url without padding of {@value #TOKEN_BYTES} bytes. */
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");

    private final RecordingLinkRepository links;
    private final CallRecordingSource source;
    private final Clock clock;
    private final String publicBaseUrl;
    private final Duration linkLifetime;
    private final SecureRandom random = new SecureRandom();

    public CallRecordingService(RecordingLinkRepository links, CallRecordingSource source, Clock clock,
                                @Value("${app.public-base-url:}") String publicBaseUrl,
                                @Value("${app.report.recording-link-ttl:P30D}") Duration linkLifetime) {
        this.links = links;
        this.source = source;
        this.clock = clock;
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.strip().replaceAll("/+$", "");
        this.linkLifetime = linkLifetime;
    }

    /**
     * The text for the report's recording line: a new link, or why there is none. No link is issued
     * without a public base URL, because a localhost link is useless to the reader.
     */
    public String linkTextFor(PhoneCall call) {
        if (call == null || call.conversationId() == null) {
            return "No call recording";
        }
        if (publicBaseUrl.isEmpty()) {
            return "Recording link unavailable (no public URL configured)";
        }
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        links.save(new RecordingLink(hash(token), call.id(), call.conversationId(), now.plus(linkLifetime), now));
        return publicBaseUrl + PATH + token;
    }

    @Override
    public Optional<CallRecording> open(String token) {
        if (token == null || !TOKEN.matcher(token).matches()) {
            return Optional.empty();
        }
        return links.findByTokenHash(hash(token))
                .filter(link -> !link.isExpired(clock.instant()))
                .flatMap(link -> source.fetch(link.conversationId()));
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
