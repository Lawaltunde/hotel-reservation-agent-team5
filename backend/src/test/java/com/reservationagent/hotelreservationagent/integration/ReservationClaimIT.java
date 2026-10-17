package com.reservationagent.hotelreservationagent.integration;

import com.reservationagent.hotelreservationagent.application.port.out.ReservationRepository;
import com.reservationagent.hotelreservationagent.domain.offer.EvidenceStatus;
import com.reservationagent.hotelreservationagent.domain.reservation.Reservation;
import com.reservationagent.hotelreservationagent.domain.reservation.ReservationState;
import com.reservationagent.hotelreservationagent.domain.shared.Money;
import com.reservationagent.hotelreservationagent.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The double-booking guard: {@link ReservationRepository#claim} against real PostgreSQL, where
 * {@code UNIQUE(task_id)} with {@code ON CONFLICT DO NOTHING} must let exactly one claim win.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ReservationClaimIT {

    private static final int CONTENDERS = 16;

    @Autowired
    private ReservationRepository reservations;

    @Autowired
    private JdbcClient jdbc;

    /** A task with one offer: the rows a reservation must reference. */
    private record Booking(UUID taskId, UUID hotelId, UUID offerId) {
    }

    private Booking insertTaskWithOffer() {
        UUID municipalityId = jdbc.sql("SELECT id FROM municipalities WHERE name = 'Ottawa'").query(UUID.class).single();
        UUID hotelId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        LocalDate checkIn = LocalDate.now().plusDays(30);

        jdbc.sql("""
                        INSERT INTO hotels (id, canonical_key, name, municipality_id, location, property_type, last_seen_at)
                        VALUES (:id, :key, 'Claim Test Hotel', :municipality, ST_MakePoint(-75.69, 45.42)::geography, 'HOTEL', :now)
                        """)
                .param("id", hotelId).param("key", "test:" + hotelId).param("municipality", municipalityId)
                .param("now", now).update();
        jdbc.sql("""
                        INSERT INTO booking_tasks (id, request_id, municipality_id, check_in, check_out, rooms, adults,
                            children, destination_email, authorization_mode, deadline, status, created_at, updated_at)
                        VALUES (:id, :requestId, :municipality, :checkIn, :checkOut, 1, 2, 0, 'claim@example.com',
                            'BOOK_IF_WITHIN_LIMITS', :deadline, 'OFFER_SELECTED', :now, :now)
                        """)
                .param("id", taskId).param("requestId", "claim-" + taskId).param("municipality", municipalityId)
                .param("checkIn", checkIn).param("checkOut", checkIn.plusDays(2))
                .param("deadline", Timestamp.from(Instant.now().plus(2, ChronoUnit.HOURS))).param("now", now).update();
        jdbc.sql("""
                        INSERT INTO phone_calls (id, task_id, hotel_id, dialled_number, status)
                        VALUES (:id, :task, :hotel, '+16135550100', 'COMPLETED')
                        """)
                .param("id", callId).param("task", taskId).param("hotel", hotelId).update();
        jdbc.sql("""
                        INSERT INTO hotel_offers (id, task_id, hotel_id, call_id, availability_confirmed, guest_count,
                            tax_inclusion, holds_room, evidence_status, received_at)
                        VALUES (:id, :task, :hotel, :call, true, 2, 'INCLUDED', false, 'VERBAL_ONLY', :now)
                        """)
                .param("id", offerId).param("task", taskId).param("hotel", hotelId).param("call", callId)
                .param("now", now).update();
        return new Booking(taskId, hotelId, offerId);
    }

    private static Reservation attempt(Booking b, String total) {
        return new Reservation(UUID.randomUUID(), b.taskId(), b.hotelId(), b.offerId(), null, Money.cad(total),
                ReservationState.ATTEMPT_IN_PROGRESS, EvidenceStatus.VERBAL_ONLY, Instant.now().truncatedTo(ChronoUnit.MICROS));
    }

    private int rowsFor(UUID taskId) {
        return jdbc.sql("SELECT count(*) FROM reservations WHERE task_id = :task").param("task", taskId)
                .query(Integer.class).single();
    }

    @Test
    void concurrentClaimsForOneTaskLetExactlyOneWin() throws Exception {
        Booking booking = insertTaskWithOffer();
        List<Reservation> attempts = new ArrayList<>();
        for (int i = 0; i < CONTENDERS; i++) {
            attempts.add(attempt(booking, "300.0" + (i % 10)));
        }

        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(CONTENDERS)) {
            for (Reservation r : attempts) {
                results.add(pool.submit(() -> {
                    start.await();
                    return reservations.claim(r);
                }));
            }
            start.countDown();
        }

        List<Reservation> winners = new ArrayList<>();
        for (int i = 0; i < CONTENDERS; i++) {
            if (results.get(i).get(30, TimeUnit.SECONDS)) {
                winners.add(attempts.get(i));
            }
        }
        assertThat(winners).hasSize(1);
        assertThat(rowsFor(booking.taskId())).isEqualTo(1);
        assertThat(reservations.findByTask(booking.taskId())).hasValueSatisfying(stored -> {
            assertThat(stored.id()).isEqualTo(winners.getFirst().id());
            assertThat(stored.total()).isEqualTo(winners.getFirst().total());
            assertThat(stored.state()).isEqualTo(ReservationState.ATTEMPT_IN_PROGRESS);
            assertThat(stored.confirmationNumber()).isNull();
        });
    }

    @Test
    void laterClaimLosesAndLeavesTheFirstUntouched() {
        Booking booking = insertTaskWithOffer();
        Reservation first = attempt(booking, "250.00");
        Reservation second = attempt(booking, "199.00");

        assertThat(reservations.claim(first)).isTrue();
        assertThat(reservations.claim(second)).isFalse();
        assertThat(reservations.claim(first)).as("re-claiming with the same row").isFalse();

        assertThat(rowsFor(booking.taskId())).isEqualTo(1);
        assertThat(reservations.findByTask(booking.taskId())).hasValueSatisfying(stored -> {
            assertThat(stored.id()).isEqualTo(first.id());
            assertThat(stored.total()).isEqualTo(Money.cad("250.00"));
        });
    }

    @Test
    void claimsForDifferentTasksDoNotBlockEachOther() {
        Booking a = insertTaskWithOffer();
        Booking b = insertTaskWithOffer();

        assertThat(reservations.claim(attempt(a, "100.00"))).isTrue();
        assertThat(reservations.claim(attempt(b, "100.00"))).isTrue();
        assertThat(rowsFor(a.taskId())).isEqualTo(1);
        assertThat(rowsFor(b.taskId())).isEqualTo(1);
    }

    @Test
    void claimedRowCanBeUpdatedThroughSave() {
        Booking booking = insertTaskWithOffer();
        Reservation claimed = attempt(booking, "275.00");
        assertThat(reservations.claim(claimed)).isTrue();

        Reservation confirmed = new Reservation(claimed.id(), claimed.taskId(), claimed.hotelId(), claimed.offerId(),
                "HX-1", claimed.total(), ReservationState.CONFIRMED, EvidenceStatus.REFERENCE_ID_GIVEN, Instant.now());
        reservations.save(confirmed);

        assertThat(rowsFor(booking.taskId())).isEqualTo(1);
        assertThat(reservations.findByTask(booking.taskId())).hasValueSatisfying(stored -> {
            assertThat(stored.isConfirmed()).isTrue();
            assertThat(stored.confirmationNumber()).isEqualTo("HX-1");
        });
    }
}
