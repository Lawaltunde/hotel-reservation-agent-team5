package com.reservationagent.hotelreservationagent.application.service.intake;

import com.reservationagent.hotelreservationagent.application.port.in.SubmitBookingTaskUseCase;
import com.reservationagent.hotelreservationagent.application.port.in.SubmitBookingTaskUseCase.Command;
import com.reservationagent.hotelreservationagent.application.port.in.SubmitTaskPromptUseCase;
import com.reservationagent.hotelreservationagent.application.port.out.AiUsageRepository;
import com.reservationagent.hotelreservationagent.application.port.out.BookingTaskRepository;
import com.reservationagent.hotelreservationagent.application.port.out.TaskPromptInterpreter;
import com.reservationagent.hotelreservationagent.application.port.out.TaskPromptInterpreter.InterpretedPrompt;
import com.reservationagent.hotelreservationagent.application.service.validation.BookingRequestValidator;
import com.reservationagent.hotelreservationagent.application.service.validation.RequestValidationException;
import com.reservationagent.hotelreservationagent.application.service.validation.RequestValidationException.FieldError;
import com.reservationagent.hotelreservationagent.domain.geography.Municipality;
import com.reservationagent.hotelreservationagent.domain.shared.Money;
import com.reservationagent.hotelreservationagent.domain.task.AuthorizationMode;
import com.reservationagent.hotelreservationagent.domain.task.BookingTask;
import com.reservationagent.hotelreservationagent.domain.task.DateExpression;
import com.reservationagent.hotelreservationagent.domain.task.TaskIntake;
import com.reservationagent.hotelreservationagent.domain.task.TaskIntake.EmailSource;
import com.reservationagent.hotelreservationagent.domain.task.TaskIntake.StatedField;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Turns one free-text request (possibly several chat messages joined by newlines, the latest
 * winning) into a {@link Command} and submits it through the normal path, so a prompt gets the
 * validation and Ontario check of a form submission.
 *
 * <p>Java owns everything that must be exact: the email address is found with a regular expression
 * and replaced with {@value #EMAIL_PLACEHOLDER} before the prompt reaches the AI model, dates are
 * resolved against today in Ontario, and what the prompt left out is handled by {@link IntakeDefaults}
 * and recorded as unstated rather than presented as the user's.
 */
@Service
public class TaskPromptService implements SubmitTaskPromptUseCase {

    static final String EMAIL_PLACEHOLDER = "<email>";
    /** Keeps prompt idempotency keys apart from form request IDs. */
    static final String IDEMPOTENCY_PREFIX = "prompt:";
    static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    /** Validator field names that this endpoint's {@code request} object calls something else. */
    private static final Map<String, String> FIELD_NAMES = Map.of("destinationEmail", "email", "maximumTotalCad", "maxBudget");

    private final TaskPromptInterpreter interpreter;
    private final SubmitBookingTaskUseCase submitTask;
    private final BookingRequestValidator validator;
    private final BookingTaskRepository tasks;
    private final Clock clock;
    private final Duration defaultDeadline;
    private final String organizerEmail;
    private final AiUsageRepository usage;

    public TaskPromptService(TaskPromptInterpreter interpreter, SubmitBookingTaskUseCase submitTask,
                             BookingRequestValidator validator, BookingTaskRepository tasks, Clock clock,
                             @Value("${app.intake.default-deadline:PT10M}") Duration defaultDeadline,
                             @Value("${app.report.recipient:}") String organizerEmail, AiUsageRepository usage) {
        this.usage = usage;
        this.interpreter = interpreter;
        this.submitTask = submitTask;
        this.validator = validator;
        this.tasks = tasks;
        this.clock = clock;
        this.defaultDeadline = defaultDeadline;
        this.organizerEmail = organizerEmail == null || organizerEmail.isBlank() ? null
                : organizerEmail.strip().toLowerCase(Locale.ROOT);
    }

    @Override
    public UUID submit(String prompt, String idempotencyKey) {
        if (idempotencyKey != null && idempotencyKey.strip().length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new RequestValidationException(List.of(new FieldError("Idempotency-Key",
                    "must be at most " + MAX_IDEMPOTENCY_KEY_LENGTH + " characters")));
        }
        String requestId = idempotencyKey == null || idempotencyKey.isBlank()
                ? UUID.randomUUID().toString()
                : IDEMPOTENCY_PREFIX + idempotencyKey.strip();
        Optional<BookingTask> existing = tasks.findByRequestId(requestId);
        if (existing.isPresent()) {
            return existing.get().id();
        }

        List<FieldError> errors = new ArrayList<>();
        Set<StatedField> stated = EnumSet.noneOf(StatedField.class);
        List<String> assumptions = new ArrayList<>();

        String email = latestEmail(prompt);
        EmailSource emailSource = EmailSource.STATED;
        if (email != null) {
            stated.add(StatedField.EMAIL);
        } else if (organizerEmail != null) {
            email = organizerEmail;
            emailSource = EmailSource.ORGANIZER_FALLBACK;
            assumptions.add("No email address given: sending the report to the organizer inbox");
        } else {
            errors.add(new FieldError("email", "Include the email address the result should be sent to"));
        }

        TaskPromptInterpreter.Interpretation interpretation =
                interpreter.interpret(EMAIL.matcher(prompt).replaceAll(EMAIL_PLACEHOLDER));
        InterpretedPrompt p = interpretation.prompt();

        LocalDate today = LocalDate.now(clock.withZone(Municipality.DEFAULT_TIME_ZONE));
        LocalDate checkIn = resolve(p.checkIn(), today, today, "check-in", assumptions);
        LocalDate checkOut = checkIn == null ? null
                : resolve(p.checkOut(), today, checkIn.plusDays(1), "check-out", assumptions);
        Integer nights = p.nights() != null && p.nights() > 0 ? p.nights() : null;
        if (checkIn != null) stated.add(StatedField.CHECK_IN);
        if (checkOut != null || (checkIn != null && nights != null)) stated.add(StatedField.CHECK_OUT);
        IntakeDefaults.Stay stay = IntakeDefaults.stay(today, checkIn, checkOut, nights,
                p.checkIn() != null && isWeekend(p.checkIn()));
        assumptions.addAll(stay.assumptions());

        if (p.rooms() != null) stated.add(StatedField.ROOMS);
        if (p.adults() != null) {
            stated.add(StatedField.ADULTS);
        } else {
            assumptions.add("Number of guests not given: asking for the cheapest available room");
        }
        if (p.children() != null) stated.add(StatedField.CHILDREN);

        long stayNights = ChronoUnit.DAYS.between(stay.checkIn(), stay.checkOut());
        BigDecimal maxBudget = IntakeDefaults.stayTotal(p.budget(), stayNights);
        BigDecimal perNight = IntakeDefaults.perNight(p.budget());
        if (maxBudget != null) stated.add(StatedField.MAX_BUDGET);

        TaskIntake intake = new TaskIntake(prompt, stated, emailSource, p.notes(),
                perNight == null || perNight.signum() <= 0 ? null : Money.cad(perNight), assumptions);
        Command command = new Command(
                requestId,
                p.city(),
                stay.checkIn(),
                stay.checkOut(),
                p.rooms() == null ? IntakeDefaults.ROOMS : p.rooms(),
                p.adults() == null ? IntakeDefaults.ADULTS : p.adults(),
                p.children() == null ? IntakeDefaults.CHILDREN : p.children(),
                email,
                clock.instant().plus(defaultDeadline),
                amenities(p.amenities()),
                AuthorizationMode.ENQUIRY_ONLY,
                maxBudget,
                false,
                intake);

        if (!errors.isEmpty()) {
            // Report every problem at once.
            try {
                validator.validate(command);
            } catch (RequestValidationException e) {
                throw merged(errors, e.errors());
            }
            throw new RequestValidationException(errors);
        }
        UUID taskId;
        try {
            taskId = submitTask.submit(command);
        } catch (RequestValidationException e) {
            throw merged(List.of(), e.errors());
        }
        usage.record(taskId, "prompt-intake", interpretation.usage(), 0);
        return taskId;
    }

    /**
     * A stated date, or {@code null} if none was stated. A date that cannot be resolved is treated as
     * unstated (the prompt cannot be corrected on the day) and noted as an assumption.
     */
    private static LocalDate resolve(DateExpression date, LocalDate today, LocalDate reference, String label,
                                     List<String> assumptions) {
        if (date == null || date.kind() == null) {
            return null;
        }
        Optional<LocalDate> resolved = date.resolve(today, reference);
        if (resolved.isEmpty()) {
            assumptions.add("The " + label + " date could not be understood and was ignored");
        }
        return resolved.orElse(null);
    }

    private static boolean isWeekend(DateExpression date) {
        return date.kind() == DateExpression.Kind.THIS_WEEKEND || date.kind() == DateExpression.Kind.NEXT_WEEKEND;
    }

    /** The last address in the prompt: in a chat transcript the latest message wins. Lower-cased. */
    private static String latestEmail(String prompt) {
        String last = null;
        Matcher m = EMAIL.matcher(prompt);
        while (m.find()) {
            last = m.group();
        }
        return last == null ? null : last.toLowerCase(Locale.ROOT);
    }

    private static Set<String> amenities(List<String> requested) {
        if (requested == null) return Set.of();
        return requested.stream()
                .filter(a -> a != null && !a.isBlank())
                .map(String::strip)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** Intake errors first, then validator errors renamed to this endpoint's fields, one per field. */
    private static RequestValidationException merged(List<FieldError> intake, List<FieldError> validation) {
        List<FieldError> all = new ArrayList<>(intake);
        Set<String> reported = intake.stream().map(FieldError::field).collect(Collectors.toCollection(HashSet::new));
        for (FieldError e : validation) {
            String field = e.field() == null ? null : FIELD_NAMES.getOrDefault(e.field(), e.field());
            if (field == null || !reported.contains(field)) {
                all.add(new FieldError(field, e.message()));
            }
        }
        return new RequestValidationException(all);
    }
}
