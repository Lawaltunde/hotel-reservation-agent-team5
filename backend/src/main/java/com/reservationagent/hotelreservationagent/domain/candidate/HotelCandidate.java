package com.reservationagent.hotelreservationagent.domain.candidate;

import java.util.UUID;

/**
 * A hotel considered for one task.
 *
 * @param callPriority 1 = call first; null when excluded
 */
public record HotelCandidate(
        UUID id,
        UUID taskId,
        UUID hotelId,
        PriceSignal priceSignal,
        double distanceKm,
        Integer callPriority,
        ExclusionReason exclusionReason
) {

    public boolean isEligible() {
        return exclusionReason == null;
    }
}
