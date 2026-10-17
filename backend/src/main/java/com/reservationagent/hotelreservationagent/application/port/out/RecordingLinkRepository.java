package com.reservationagent.hotelreservationagent.application.port.out;

import com.reservationagent.hotelreservationagent.domain.call.RecordingLink;

import java.util.Optional;

public interface RecordingLinkRepository {

    RecordingLink save(RecordingLink link);

    Optional<RecordingLink> findByTokenHash(String tokenHash);
}
