package com.reservationagent.hotelreservationagent.application.port.out;

import com.reservationagent.hotelreservationagent.domain.candidate.HotelCandidate;

import java.util.List;
import java.util.UUID;

public interface HotelCandidateRepository {

    void saveAll(List<HotelCandidate> candidates);

    /** Eligible candidates in call order. */
    List<HotelCandidate> findEligibleInCallOrder(UUID taskId);

    int countByTask(UUID taskId);
}
