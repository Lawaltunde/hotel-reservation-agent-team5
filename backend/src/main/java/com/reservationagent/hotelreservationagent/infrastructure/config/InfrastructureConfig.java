package com.reservationagent.hotelreservationagent.infrastructure.config;

import com.reservationagent.hotelreservationagent.application.policy.AgentPolicy;
import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * WebClient instances for each external API. All share Reactor Netty with strict connect and
 * response timeouts so a slow provider never stalls the call pipeline.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(HotelAgentProperties.class)
public class InfrastructureConfig {

    @Bean
    AgentPolicy agentPolicy(HotelAgentProperties properties) {
        HotelAgentProperties.Policy p = properties.policy();
        return new AgentPolicy(p.searchRadiusKm(), p.candidateLimit(), p.enrichmentLimit(), p.enrichmentTimeout(),
                p.maxParallelCalls(), p.maxTotalCalls(), p.maxNegotiationRounds(), p.maxCallDuration(),
                p.deadlineReserve(), p.allowedPropertyTypes());
    }

    @Bean
    ReactorClientHttpConnector reactorClientHttpConnector(HotelAgentProperties properties) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) properties.http().connectTimeout().toMillis())
                .responseTimeout(properties.http().responseTimeout());
        return new ReactorClientHttpConnector(httpClient);
    }

    @Bean
    @Qualifier("googlePlaces")
    WebClient googlePlacesWebClient(WebClient.Builder builder, ReactorClientHttpConnector connector, HotelAgentProperties p) {
        return builder.clone().clientConnector(connector).baseUrl(p.googlePlaces().baseUrl())
                .defaultHeader("X-Goog-Api-Key", nullToEmpty(p.googlePlaces().apiKey()))
                .build();
    }

    @Bean
    @Qualifier("elevenLabs")
    WebClient elevenLabsWebClient(WebClient.Builder builder, ReactorClientHttpConnector connector, HotelAgentProperties p) {
        return builder.clone().clientConnector(connector).baseUrl(p.elevenlabs().baseUrl())
                .defaultHeader("xi-api-key", nullToEmpty(p.elevenlabs().apiKey()))
                .build();
    }

    @Bean
    @Qualifier("resend")
    WebClient resendWebClient(WebClient.Builder builder, ReactorClientHttpConnector connector, HotelAgentProperties p) {
        return builder.clone().clientConnector(connector).baseUrl(p.resend().baseUrl())
                .defaultHeaders(h -> h.setBearerAuth(nullToEmpty(p.resend().apiKey())))
                .build();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
