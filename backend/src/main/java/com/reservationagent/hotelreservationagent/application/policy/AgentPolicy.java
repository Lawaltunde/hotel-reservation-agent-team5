package com.reservationagent.hotelreservationagent.application.policy;

import com.reservationagent.hotelreservationagent.domain.hotel.PropertyType;

import java.time.Duration;
import java.util.Set;

/**
 * Tunable operating limits. Values come from configuration (see {@code hotel-agent.policy.*});
 * the defaults in application.yml are unvalidated starter values to be tuned from benchmarks.
 */
public record AgentPolicy(
        double searchRadiusKm,
        int candidateLimit,
        int enrichmentLimit,
        Duration enrichmentTimeout,
        int maxParallelCalls,
        int maxTotalCalls,
        int maxNegotiationRounds,
        Duration maxCallDuration,
        Duration deadlineReserve,
        Set<PropertyType> allowedPropertyTypes
) {

    public AgentPolicy {
        allowedPropertyTypes = Set.copyOf(allowedPropertyTypes);
    }
}
