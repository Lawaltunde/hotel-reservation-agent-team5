package com.reservationagent.hotelreservationagent.application.port.out;

import com.reservationagent.hotelreservationagent.domain.transcript.Transcript;

import java.util.List;
import java.util.UUID;

public interface TranscriptRepository {

    Transcript save(Transcript transcript);

    List<Transcript> findByTask(UUID taskId);
}
