package com.reservationagent.hotelreservationagent.config;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Cross-cutting wiring. {@code @EnableAsync} runs the task orchestrator off the request thread
 * (on virtual threads, see {@code spring.threads.virtual.enabled}); {@code @EnableScheduling}
 * drives the deadline watchdog.
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
@EnableScheduling
public class ApplicationConfig {

    private static final Logger log = LoggerFactory.getLogger(ApplicationConfig.class);

    /** Single source of time, so services and tests agree on "now". */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Logs the effective AI settings (never the key) so a misconfiguration is visible at startup. */
    @Bean
    ApplicationRunner logAiConfiguration(Environment env) {
        return args -> {
            String key = env.getProperty("spring.ai.openai.api-key", "");
            boolean keyConfigured = !key.isBlank() && !"not-set".equals(key);
            log.info("Offer extraction: model={} reasoningEffort={} temperature={} apiKeyConfigured={}",
                    env.getProperty("spring.ai.openai.chat.model"),
                    env.getProperty("spring.ai.openai.chat.reasoning-effort"),
                    env.getProperty("spring.ai.openai.chat.temperature"),
                    keyConfigured);
            if (!keyConfigured) {
                log.warn("OPENAI_API_KEY is not set: the app runs, but transcript offer extraction will fail");
            }
        };
    }
}
