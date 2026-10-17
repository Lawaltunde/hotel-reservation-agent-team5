package com.reservationagent.hotelreservationagent.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * {@code ${OPENAI_CHAT_MODEL:gpt-5.4-mini}} only falls back to the default when the variable is
 * undefined. A copied {@code .env.example} defines it as empty, which would send an empty model
 * name. This post-processor restores the defaults for variables that are present but blank.
 * Runs after config data, so values imported from {@code .env} are seen.
 */
public class BlankEnvDefaultsPostProcessor implements EnvironmentPostProcessor, Ordered {

    /** variable -> (target property, default). Keep the defaults in step with application.properties. */
    private static final Map<String, String[]> DEFAULTS = Map.of(
            "OPENAI_API_KEY", new String[] {"spring.ai.openai.api-key", "not-set"},
            "OPENAI_CHAT_MODEL", new String[] {"spring.ai.openai.chat.model", "gpt-5.4-mini"},
            "OPENAI_REASONING_EFFORT", new String[] {"spring.ai.openai.chat.reasoning-effort", "none"},
            "OPENAI_TEMPERATURE", new String[] {"spring.ai.openai.chat.temperature", "0.1"},
            "APP_INTAKE_DEFAULT_DEADLINE", new String[] {"app.intake.default-deadline", "PT10M"},
            "HOTEL_AGENT_MAX_PARALLEL_CALLS", new String[] {"hotel-agent.policy.max-parallel-calls", "3"},
            "APP_REPORT_SUBJECT", new String[] {"app.report.subject", "Team 5 - Hotel Reservation Enquiry Result"},
            "APP_REPORT_RECORDING_LINK_TTL", new String[] {"app.report.recording-link-ttl", "P30D"});

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> overrides = new LinkedHashMap<>();
        DEFAULTS.forEach((variable, target) -> {
            String value = environment.getProperty(variable);
            if (value != null && value.isBlank()) {
                overrides.put(target[0], target[1]);
            }
        });
        if (!overrides.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource("blankEnvDefaults", overrides));
        }
    }

    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }
}
