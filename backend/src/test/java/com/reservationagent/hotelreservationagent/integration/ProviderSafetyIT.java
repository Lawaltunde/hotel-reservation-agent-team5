package com.reservationagent.hotelreservationagent.integration;

import com.reservationagent.hotelreservationagent.application.port.out.EmailProvider;
import com.reservationagent.hotelreservationagent.application.port.out.HotelEnrichmentProvider;
import com.reservationagent.hotelreservationagent.application.port.out.VoiceCallProvider;
import com.reservationagent.hotelreservationagent.infrastructure.config.HotelAgentProperties;
import com.reservationagent.hotelreservationagent.support.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real providers stay off in tests even when the environment turns them on, as a root {@code .env}
 * with {@code ELEVENLABS_ENABLED=true} would. System properties stand in for that file here: they
 * feed the same {@code ${ELEVENLABS_ENABLED:false}} placeholders, and also set the
 * {@code hotel-agent.*} keys directly.
 */
@SpringBootTest(properties = "provider-safety-it=true") // own context, so the properties below apply
@Import(TestcontainersConfiguration.class)
class ProviderSafetyIT {

    private static final List<String> LIVE_SWITCHES = List.of(
            "ELEVENLABS_ENABLED", "GOOGLE_PLACES_ENABLED", "RESEND_ENABLED",
            "hotel-agent.elevenlabs.enabled", "hotel-agent.google-places.enabled", "hotel-agent.resend.enabled");

    static {
        LIVE_SWITCHES.forEach(key -> System.setProperty(key, "true"));
        System.setProperty("OPENAI_API_KEY", "sk-live-looking-key");
    }

    @AfterAll
    static void clearSwitches() {
        LIVE_SWITCHES.forEach(System::clearProperty);
        System.clearProperty("OPENAI_API_KEY");
    }

    @Autowired
    private HotelAgentProperties properties;
    @Autowired
    private Environment env;
    @Autowired
    private VoiceCallProvider voice;
    @Autowired
    private EmailProvider email;
    @Autowired
    private HotelEnrichmentProvider enrichment;

    @Test
    void everyProviderIsForcedOff() {
        assertThat(System.getProperty("ELEVENLABS_ENABLED")).isEqualTo("true");

        assertThat(properties.elevenlabs().enabled()).isFalse();
        assertThat(properties.googlePlaces().enabled()).isFalse();
        assertThat(properties.resend().enabled()).isFalse();
    }

    @Test
    void theLocalStandInsAreWired() {
        assertThat(voice.getClass().getSimpleName()).isEqualTo("LoggingVoiceCallProvider");
        assertThat(email.getClass().getSimpleName()).isEqualTo("LoggingEmailProvider");
        assertThat(enrichment.getClass().getSimpleName()).isEqualTo("NoOpEnrichmentProvider");
    }

    @Test
    void openAiIsPointedAtADeadLocalPortWithAFakeKey() {
        assertThat(env.getProperty("spring.ai.openai.base-url")).isEqualTo("http://127.0.0.1:9");
        assertThat(env.getProperty("spring.ai.openai.api-key")).isEqualTo("sk-test-not-a-real-key");
    }
}
