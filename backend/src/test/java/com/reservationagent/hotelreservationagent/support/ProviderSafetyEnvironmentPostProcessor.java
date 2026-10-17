package com.reservationagent.hotelreservationagent.support;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Test-only guard: no test may reach a real provider, whatever the root {@code .env}, OS
 * environment or system properties say. Under JUnit, each provider is forced off unless its base
 * URL points at this machine (a WireMock stand-in), SMTP is switched off unless its host is this
 * machine, and Spring AI is pointed at a dead local port.
 * Added as the highest-precedence property source, so it beats {@code .env} imports, environment
 * variables and {@code -D} flags. Registered in {@code src/test/resources/META-INF/spring.factories}.
 */
public class ProviderSafetyEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    public static final String PROPERTY_SOURCE = "testProviderSafety";

    /** Nothing listens here, so a stray OpenAI call fails fast instead of reaching the internet. */
    static final String DEAD_OPENAI_URL = "http://127.0.0.1:9";

    private static final Map<String, String> PROVIDERS = Map.of(
            "hotel-agent.elevenlabs", "https://api.elevenlabs.io",
            "hotel-agent.google-places", "https://places.googleapis.com",
            "hotel-agent.resend", "https://api.resend.com");
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "[::1]");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
        if (!runningUnderJUnit()) {
            return;
        }
        Map<String, Object> overrides = new LinkedHashMap<>();
        PROVIDERS.forEach((prefix, defaultUrl) -> {
            if (!isLocal(env.getProperty(prefix + ".base-url", defaultUrl))) {
                overrides.put(prefix + ".enabled", "false");
            }
        });
        // SMTP (app.email.transport=smtp): allowed only against a local mail server.
        if (!LOCAL_HOSTS.contains(env.getProperty("spring.mail.host", "").strip().toLowerCase())) {
            overrides.put("app.email.transport", "resend");
            overrides.put("spring.mail.host", "127.0.0.1");
            overrides.put("spring.mail.port", "9");
        }
        if (!isLocal(env.getProperty("spring.ai.openai.base-url", "https://api.openai.com"))) {
            overrides.put("spring.ai.openai.base-url", DEAD_OPENAI_URL);
            overrides.put("spring.ai.openai.api-key", "sk-test-not-a-real-key");
        }
        env.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE, overrides));
    }

    static boolean isLocal(String url) {
        try {
            String host = URI.create(url.trim()).getHost();
            return host != null && LOCAL_HOSTS.contains(host.toLowerCase());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean runningUnderJUnit() {
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            if (frame.getClassName().startsWith("org.junit.")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
