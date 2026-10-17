package com.reservationagent.hotelreservationagent.infrastructure.persistence.reservation;

import com.reservationagent.hotelreservationagent.application.port.out.ReservationRepository;
import com.reservationagent.hotelreservationagent.domain.reservation.Reservation;
import com.reservationagent.hotelreservationagent.domain.shared.Money;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

@Repository
class ReservationPersistenceAdapter implements ReservationRepository {

    private final ReservationJpaRepository jpa;
    private final JdbcClient jdbc;

    ReservationPersistenceAdapter(ReservationJpaRepository jpa, JdbcClient jdbc) {
        this.jpa = jpa;
        this.jdbc = jdbc;
    }

    /** Relies on UNIQUE(task_id): concurrent claims for the same task insert at most one row. */
    @Override
    public boolean claim(Reservation r) {
        int inserted = jdbc.sql("""
                        INSERT INTO reservations (id, task_id, hotel_id, offer_id, confirmation_number, total, state, evidence, updated_at)
                        VALUES (:id, :taskId, :hotelId, :offerId, NULL, :total, :state, :evidence, :updatedAt)
                        ON CONFLICT (task_id) DO NOTHING
                        """)
                .param("id", r.id())
                .param("taskId", r.taskId())
                .param("hotelId", r.hotelId())
                .param("offerId", r.offerId())
                .param("total", r.total().amount())
                .param("state", r.state().name())
                .param("evidence", r.evidence().name())
                .param("updatedAt", Timestamp.from(r.updatedAt()))
                .update();
        return inserted == 1;
    }

    @Override
    public Reservation save(Reservation r) {
        ReservationEntity e = jpa.findById(r.id()).orElseGet(ReservationEntity::new);
        e.id = r.id();
        e.taskId = r.taskId();
        e.hotelId = r.hotelId();
        e.offerId = r.offerId();
        e.confirmationNumber = r.confirmationNumber();
        e.total = r.total().amount();
        e.state = r.state();
        e.evidence = r.evidence();
        e.updatedAt = r.updatedAt();
        jpa.save(e);
        return r;
    }

    @Override
    public Optional<Reservation> findByTask(UUID taskId) {
        return jpa.findByTaskId(taskId).map(e -> new Reservation(e.id, e.taskId, e.hotelId, e.offerId,
                e.confirmationNumber, Money.cad(e.total), e.state, e.evidence, e.updatedAt));
    }
}
