package com.reservationagent.hotelreservationagent.infrastructure.persistence.task;

import com.reservationagent.hotelreservationagent.application.port.out.BookingTaskRepository;
import com.reservationagent.hotelreservationagent.domain.shared.Money;
import com.reservationagent.hotelreservationagent.domain.task.BookingTask;
import com.reservationagent.hotelreservationagent.domain.task.StayDetails;
import com.reservationagent.hotelreservationagent.domain.task.TaskIntake;
import com.reservationagent.hotelreservationagent.domain.task.TaskStatus;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
class BookingTaskPersistenceAdapter implements BookingTaskRepository {

    private static final Set<TaskStatus> TERMINAL = EnumSet.of(TaskStatus.COMPLETED, TaskStatus.INVALID_REQUEST,
            TaskStatus.OUT_OF_SCOPE_LOCATION, TaskStatus.EMAIL_FAILED, TaskStatus.DEADLINE_EXCEEDED);

    private final BookingTaskJpaRepository jpa;

    BookingTaskPersistenceAdapter(BookingTaskJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public BookingTask save(BookingTask task) {
        BookingTaskEntity e = jpa.findById(task.id()).orElseGet(BookingTaskEntity::new);
        e.id = task.id();
        e.requestId = task.requestId();
        e.municipalityId = task.municipalityId();
        e.checkIn = task.stay().checkIn();
        e.checkOut = task.stay().checkOut();
        e.rooms = task.stay().rooms();
        e.adults = task.stay().adults();
        e.children = task.stay().children();
        e.destinationEmail = task.destinationEmail();
        e.authorizationMode = task.authorizationMode();
        e.maximumTotal = task.maximumTotal() == null ? null : task.maximumTotal().amount();
        e.requiredAmenities = task.requiredAmenities().toArray(String[]::new);
        e.allowCompetingQuoteDisclosure = task.allowCompetingQuoteDisclosure();
        TaskIntake intake = task.intake();
        e.originalPrompt = intake.originalPrompt();
        e.statedFields = intake.stated().stream().map(Enum::name).toArray(String[]::new);
        e.emailSource = intake.emailSource().name();
        e.notes = intake.notes();
        e.maximumPerNight = intake.maximumPerNight() == null ? null : intake.maximumPerNight().amount();
        e.assumptions = intake.assumptions().toArray(String[]::new);
        e.deadline = task.deadline();
        e.status = task.status();
        e.outcome = task.outcome();
        e.createdAt = task.createdAt();
        e.updatedAt = task.updatedAt();
        return toDomain(jpa.save(e));
    }

    @Override
    public Optional<BookingTask> findById(UUID id) {
        return jpa.findById(id).map(BookingTaskPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<BookingTask> findByRequestId(String requestId) {
        return jpa.findByRequestId(requestId).map(BookingTaskPersistenceAdapter::toDomain);
    }

    @Override
    public List<BookingTask> findActive() {
        return jpa.findByStatusNotIn(TERMINAL).stream().map(BookingTaskPersistenceAdapter::toDomain).toList();
    }

    private static BookingTask toDomain(BookingTaskEntity e) {
        return new BookingTask(e.id, e.requestId, e.municipalityId,
                new StayDetails(e.checkIn, e.checkOut, e.rooms, e.adults, e.children),
                e.destinationEmail, e.authorizationMode, e.maximumTotal == null ? null : Money.cad(e.maximumTotal),
                e.requiredAmenities == null ? Set.of() : Set.copyOf(Arrays.asList(e.requiredAmenities)),
                e.allowCompetingQuoteDisclosure, intake(e), e.deadline, e.status, e.outcome, e.createdAt, e.updatedAt);
    }

    private static TaskIntake intake(BookingTaskEntity e) {
        if (e.statedFields == null) {
            return TaskIntake.FORM;
        }
        Set<TaskIntake.StatedField> stated = EnumSet.noneOf(TaskIntake.StatedField.class);
        Arrays.stream(e.statedFields).map(TaskIntake.StatedField::valueOf).forEach(stated::add);
        return new TaskIntake(e.originalPrompt, stated,
                e.emailSource == null ? null : TaskIntake.EmailSource.valueOf(e.emailSource), e.notes,
                e.maximumPerNight == null ? null : Money.cad(e.maximumPerNight),
                e.assumptions == null ? List.of() : Arrays.asList(e.assumptions));
    }
}
