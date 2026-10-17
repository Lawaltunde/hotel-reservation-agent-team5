package com.reservationagent.hotelreservationagent.application.service.task;

import com.reservationagent.hotelreservationagent.application.port.in.GetBookingTaskUseCase;
import com.reservationagent.hotelreservationagent.application.port.in.SubmitBookingTaskUseCase;
import com.reservationagent.hotelreservationagent.application.port.out.BookingTaskRepository;
import com.reservationagent.hotelreservationagent.application.port.out.EmailDeliveryRepository;
import com.reservationagent.hotelreservationagent.application.port.out.ReportRepository;
import com.reservationagent.hotelreservationagent.application.port.out.HotelCandidateRepository;
import com.reservationagent.hotelreservationagent.application.port.out.HotelIndex;
import com.reservationagent.hotelreservationagent.application.port.out.MunicipalityRepository;
import com.reservationagent.hotelreservationagent.application.port.out.OfferRepository;
import com.reservationagent.hotelreservationagent.application.port.out.PhoneCallRepository;
import com.reservationagent.hotelreservationagent.application.port.out.ReservationRepository;
import com.reservationagent.hotelreservationagent.application.service.pricing.OfferEvaluator;
import com.reservationagent.hotelreservationagent.application.service.validation.BookingRequestValidator;
import com.reservationagent.hotelreservationagent.domain.geography.Municipality;
import com.reservationagent.hotelreservationagent.domain.hotel.Hotel;
import com.reservationagent.hotelreservationagent.domain.offer.HotelOffer;
import com.reservationagent.hotelreservationagent.domain.shared.Money;
import com.reservationagent.hotelreservationagent.domain.task.BookingTask;
import com.reservationagent.hotelreservationagent.domain.task.StayDetails;
import com.reservationagent.hotelreservationagent.domain.task.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class BookingTaskService implements SubmitBookingTaskUseCase, GetBookingTaskUseCase {

    private static final Logger log = LoggerFactory.getLogger(BookingTaskService.class);

    private final BookingRequestValidator validator;
    private final BookingTaskRepository tasks;
    private final MunicipalityRepository municipalities;
    private final HotelCandidateRepository candidates;
    private final PhoneCallRepository calls;
    private final OfferRepository offers;
    private final ReservationRepository reservations;
    private final HotelIndex hotelIndex;
    private final OfferEvaluator offerEvaluator;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final ReportRepository reports;
    private final EmailDeliveryRepository deliveries;

    public BookingTaskService(BookingRequestValidator validator, BookingTaskRepository tasks,
                              MunicipalityRepository municipalities, HotelCandidateRepository candidates,
                              PhoneCallRepository calls, OfferRepository offers, ReservationRepository reservations,
                              HotelIndex hotelIndex, OfferEvaluator offerEvaluator, ApplicationEventPublisher events, Clock clock,
                              ReportRepository reports, EmailDeliveryRepository deliveries) {
        this.reports = reports;
        this.deliveries = deliveries;
        this.validator = validator;
        this.tasks = tasks;
        this.municipalities = municipalities;
        this.candidates = candidates;
        this.calls = calls;
        this.offers = offers;
        this.reservations = reservations;
        this.hotelIndex = hotelIndex;
        this.offerEvaluator = offerEvaluator;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional
    public UUID submit(Command command) {
        Optional<BookingTask> existing = tasks.findByRequestId(command.requestId());
        if (existing.isPresent()) {
            return existing.get().id();
        }
        Municipality municipality = validator.validate(command);
        Instant now = clock.instant();
        BookingTask task = new BookingTask(
                UUID.randomUUID(),
                command.requestId(),
                municipality.id(),
                new StayDetails(command.checkIn(), command.checkOut(), command.rooms(), command.adults(), command.children()),
                command.destinationEmail(),
                command.authorizationMode(),
                command.maximumTotalCad() == null ? null : Money.cad(command.maximumTotalCad()),
                command.requiredAmenities(),
                command.allowCompetingQuoteDisclosure(),
                command.intake(),
                command.deadline(),
                TaskStatus.RECEIVED,
                null,
                now,
                now);
        tasks.save(task.transitionTo(TaskStatus.VALIDATING, now));
        log.info("task={} {} -> {}: request {} accepted for {}", task.id(), TaskStatus.RECEIVED, TaskStatus.VALIDATING,
                command.requestId(), municipality.name());
        events.publishEvent(new TaskSubmittedEvent(task.id()));
        return task.id();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TaskSnapshot> get(UUID taskId) {
        return tasks.findById(taskId).map(task -> {
            List<HotelOffer> taskOffers = offers.findByTask(taskId);
            Map<UUID, Hotel> offerHotels = new LinkedHashMap<>();
            taskOffers.stream().map(HotelOffer::hotelId).distinct()
                    .forEach(id -> hotelIndex.findById(id).ifPresent(h -> offerHotels.put(id, h)));
            Optional<Municipality> municipality = municipalities.findById(task.municipalityId());
            return new TaskSnapshot(
                    task,
                    municipality.map(Municipality::name).orElse(null),
                    candidates.countByTask(taskId),
                    calls.findByTask(taskId).size(),
                    taskOffers,
                    offerEvaluator.best(task, taskOffers).orElse(null),
                    reservations.findByTask(taskId).orElse(null),
                    offerHotels,
                    reports.findByTask(taskId).orElse(null),
                    deliveries.findLatestByTask(taskId).orElse(null),
                    municipality.map(m -> m.provinceOrOntario().salesTaxRate()).orElse(null));
        });
    }
}
