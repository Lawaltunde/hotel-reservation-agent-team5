package com.reservationagent.hotelreservationagent.infrastructure.persistence.transcript;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

interface TranscriptJpaRepository extends JpaRepository<TranscriptEntity, UUID> {

    @Query(value = """
            SELECT t.* FROM transcripts t JOIN phone_calls c ON c.id = t.call_id
            WHERE c.task_id = :taskId ORDER BY c.started_at
            """, nativeQuery = true)
    List<TranscriptEntity> findByTaskId(UUID taskId);
}
