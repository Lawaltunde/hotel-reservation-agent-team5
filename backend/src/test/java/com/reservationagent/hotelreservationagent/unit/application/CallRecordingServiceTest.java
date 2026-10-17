package com.reservationagent.hotelreservationagent.unit.application;

import com.reservationagent.hotelreservationagent.application.port.out.CallRecordingSource;
import com.reservationagent.hotelreservationagent.application.port.out.CallRecordingSource.CallRecording;
import com.reservationagent.hotelreservationagent.application.port.out.RecordingLinkRepository;
import com.reservationagent.hotelreservationagent.application.service.recording.CallRecordingService;
import com.reservationagent.hotelreservationagent.application.service.recording.CallRecordingService.IssuedLink;
import com.reservationagent.hotelreservationagent.domain.call.CallOutcome;
import com.reservationagent.hotelreservationagent.domain.call.CallStatus;
import com.reservationagent.hotelreservationagent.domain.call.PhoneCall;
import com.reservationagent.hotelreservationagent.domain.call.RecordingLink;
import com.reservationagent.hotelreservationagent.domain.hotel.PhoneNumber;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.reservationagent.hotelreservationagent.support.Fixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unguessable, expiring links to call audio; only the token's hash is stored. */
class CallRecordingServiceTest {

    private static final String BASE = "https://agent.example.test";
    private static final CallRecording AUDIO = new CallRecording("audio/mpeg", "ID3".getBytes(StandardCharsets.US_ASCII));

    /** In-memory store keyed by token hash. */
    static final class Links implements RecordingLinkRepository {
        final Map<String, RecordingLink> byHash = new HashMap<>();

        @Override
        public RecordingLink save(RecordingLink link) {
            byHash.put(link.tokenHash(), link);
            return link;
        }

        @Override
        public Optional<RecordingLink> findByTokenHash(String tokenHash) {
            return Optional.ofNullable(byHash.get(tokenHash));
        }
    }

    private final Links links = new Links();
    private final CallRecordingSource source = mock(CallRecordingSource.class);

    private CallRecordingService service(String baseUrl, Instant now) {
        return new CallRecordingService(links, source, Clock.fixed(now, ZoneOffset.UTC), baseUrl, Duration.ofDays(30));
    }

    private static PhoneCall call(String conversationId) {
        return new PhoneCall(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), new PhoneNumber("+16135550100"),
                "CA1", conversationId, CallStatus.COMPLETED, CallOutcome.OFFER_RECEIVED, NOW, NOW.plusSeconds(60), null);
    }

    private static String token(IssuedLink link) {
        return link.url().substring((BASE + CallRecordingService.PATH).length());
    }

    @Test
    void issuesAPublicLinkWithARandomTokenAndStoresOnlyItsHash() {
        IssuedLink link = service(BASE + "//", NOW).issueFor(call("conv_1"));

        assertThat(link.url()).startsWith(BASE + "/api/recordings/").isEqualTo(link.text());
        String token = token(link);
        assertThat(token).matches("[A-Za-z0-9_-]{43}").doesNotContain("conv_1");
        RecordingLink stored = links.byHash.values().iterator().next();
        assertThat(stored.tokenHash()).isNotEqualTo(token).matches("[0-9a-f]{64}");
        assertThat(stored.conversationId()).isEqualTo("conv_1");
        assertThat(stored.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
    }

    @Test
    void everyLinkIsDifferent() {
        CallRecordingService s = service(BASE, NOW);
        PhoneCall c = call("conv_1");
        Set<String> urls = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            urls.add(s.issueFor(c).url());
        }
        assertThat(urls).hasSize(20);
    }

    @Test
    void noLinkWithoutAPublicUrlOrAConversation() {
        assertThat(service("", NOW).issueFor(call("conv_1")))
                .isEqualTo(new IssuedLink(null, "Recording link unavailable (no public URL configured)"));
        assertThat(service(BASE, NOW).issueFor(call(null))).isEqualTo(new IssuedLink(null, "No call recording"));
        assertThat(service(BASE, NOW).issueFor(null)).isEqualTo(new IssuedLink(null, "No call recording"));
        assertThat(links.byHash).isEmpty();
    }

    @Test
    void aValidTokenOpensTheAudioFromTheProvider() {
        when(source.fetch("conv_1")).thenReturn(Optional.of(AUDIO));
        String token = token(service(BASE, NOW).issueFor(call("conv_1")));

        assertThat(service(BASE, NOW.plus(Duration.ofDays(29))).open(token)).contains(AUDIO);
    }

    @Test
    void anExpiredLinkOpensNothing() {
        String token = token(service(BASE, NOW).issueFor(call("conv_1")));

        assertThat(service(BASE, NOW.plus(Duration.ofDays(30))).open(token)).isEmpty();
        verify(source, never()).fetch(anyString());
    }

    @Test
    void unknownOrMalformedTokensOpenNothing() {
        service(BASE, NOW).issueFor(call("conv_1"));
        CallRecordingService s = service(BASE, NOW);

        assertThat(s.open(null)).isEmpty();
        assertThat(s.open("short")).isEmpty();
        assertThat(s.open("../../etc/passwd")).isEmpty();
        assertThat(s.open("A".repeat(43))).isEmpty();
        verify(source, never()).fetch(anyString());
    }
}
