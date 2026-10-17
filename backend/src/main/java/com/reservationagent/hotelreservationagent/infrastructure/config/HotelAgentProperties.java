package com.reservationagent.hotelreservationagent.infrastructure.config;

import com.reservationagent.hotelreservationagent.domain.hotel.PropertyType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.Set;

/** All {@code hotel-agent.*} settings. Secrets come from environment variables, never from the repo. */
@ConfigurationProperties("hotel-agent")
public record HotelAgentProperties(
        @DefaultValue Policy policy,
        @DefaultValue Http http,
        @DefaultValue ElevenLabs elevenlabs,
        @DefaultValue GooglePlaces googlePlaces,
        @DefaultValue Resend resend,
        @DefaultValue Twilio twilio
) {

    public record Policy(
            @DefaultValue("15") double searchRadiusKm,
            @DefaultValue("60") int candidateLimit,
            @DefaultValue("6") int enrichmentLimit,
            @DefaultValue("2500ms") Duration enrichmentTimeout,
            @DefaultValue("2") int maxParallelCalls,
            @DefaultValue("8") int maxTotalCalls,
            @DefaultValue("2") int maxNegotiationRounds,
            @DefaultValue("240s") Duration maxCallDuration,
            @DefaultValue("45s") Duration deadlineReserve,
            @DefaultValue({"HOTEL", "MOTEL", "GUEST_HOUSE", "BED_AND_BREAKFAST"}) Set<PropertyType> allowedPropertyTypes
    ) {
    }

    public record Http(
            @DefaultValue("2s") Duration connectTimeout,
            @DefaultValue("10s") Duration responseTimeout
    ) {
    }

    /** {@code enabled=false} uses a logging stub so the app runs without telephony credentials. */
    public record ElevenLabs(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("https://api.elevenlabs.io") String baseUrl,
            String apiKey,
            String agentId,
            String agentPhoneNumberId,
            String webhookSecret
    ) {
    }

    public record GooglePlaces(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("https://places.googleapis.com") String baseUrl,
            String apiKey,
            @DefaultValue("300") double matchRadiusMetres
    ) {
    }

    public record Resend(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("https://api.resend.com") String baseUrl,
            String apiKey,
            String from,
            String webhookSecret
    ) {
    }

    /**
     * The Twilio account behind the ElevenLabs phone number. Outbound calls still go through the
     * ElevenLabs native Twilio integration ({@code agent_phone_number_id}); these settings are
     * reserved for status callbacks and call reconciliation.
     * TODO(phase 3): Twilio status-callback verification and reconciliation of stuck calls.
     *
     * @param phoneNumber the caller ID in E.164, e.g. a 249 Ontario number
     */
    public record Twilio(
            String accountSid,
            String apiKeySid,
            String apiKeySecret,
            String phoneNumber
    ) {
    }
}
