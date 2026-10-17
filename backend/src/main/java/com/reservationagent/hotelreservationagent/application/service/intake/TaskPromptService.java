package com.reservationagent.hotelreservationagent.application.service.intake;

import com.reservationagent.hotelreservationagent.application.port.in.SubmitBookingTaskUseCase;
import com.reservationagent.hotelreservationagent.application.port.in.SubmitBookingTaskUseCase.Command;
import com.reservationagent.hotelreservationagent.application.port.in.SubmitTaskPromptUseCase;
import com.reservationagent.hotelreservationagent.application.port.out.BookingTaskRepository;
import com.reservationagent.hotelreservationagent.application.port.out.TaskPromptInterpreter;
import com.reservationagent.hotelreservationagent.application.port.out.TaskPromptInterpreter.InterpretedPrompt;
import com.reservationagent.hotelreservationagent.application.service.validation.BookingRequestValidator;
import com.reservationagent.hotelreservationagent.application.service.validation.RequestValidationException;
import com.reservationagent.hotelreservationagent.application.service.validation.RequestValidationException.FieldError;
import com.reservationagent.hotelreservationagent.domain.geography.Municipality;
import com.reservationagent.hotelreservationagent.domain.task.AuthorizationMode;
import com.reservationagent.hotelreservationagent.domain.task.BookingTask;
import com.reservationagent.hotelreservationagent.domain.task.DateExpression;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
 * Turns one free-text request into a {@link Command} and submits it through the normal path, so a
 * prompt gets exactly the validation, Ontario check and defaults of a form submission.
 *
 * <p>Java owns everything that must be exact: the email address is found with a regular expression
 * and replaced with {@value #EMAIL_PLACEHOLDER} before the prompt reaches the AI model, dates are
 * resolved against today in Ontario, and the defaults come from {@link IntakeDefaults}.
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

    public TaskPromptService(TaskPromptInterpreter interpreter, SubmitBookingTaskUseCase submitTask,
                             BookingRequestValidator validator, BookingTaskRepository tasks, Clock clock,
                             @Value("${app.intake.default-deadline:PT30M}") Duration defaultDeadline) {
        this.interpreter = interpreter;
        this.submitTask = submitTask;
        this.validator = validator;
        this.tasks = tasks;
        this.clock = clock;
        this.defaultDeadline = defaultDeadline;
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
        String email = singleEmail(prompt, errors);
        InterpretedPrompt p = interpreter.interpret(EMAIL.matcher(prompt).replaceAll(EMAIL_PLACEHOLDER));

        LocalDate today = LocalDate.now(clock.withZone(Municipality.DEFAULT_TIME_ZONE));
        LocalDate checkIn = null;
        LocalDate checkOut = null;
        if (p.checkIn() == null) {
            errors.add(new FieldError("checkIn", "Say which day you want to check in"));
        } else {
            checkIn = p.checkIn().resolve(today, today).orElse(null);
            if (checkIn == null) errors.add(new FieldError("checkIn", "The check-in date could not be understood"));
        }
        if (checkIn != null) {
            if (p.checkOut() != null) {
                checkOut = p.checkOut().resolve(today, checkIn.plusDays(1)).orElse(null);
                if (checkOut == null) errors.add(new FieldError("checkOut", "The check-out date could not be understood"));
            } else if (p.nights() != null) {
                if (p.nights() < 1) errors.add(new FieldError("checkOut", "The stay must be at least 1 night"));
                else checkOut = checkIn.plusDays(p.nights());
            } else {
                checkOut = checkIn.plusDays(isWeekend(p.checkIn())
                        ? IntakeDefaults.NIGHTS_FOR_WEEKEND : IntakeDefaults.NIGHTS_WHEN_NO_CHECK_OUT);
            }
        }

        Command command = new Command(
                requestId,
                p.city(),
                checkIn,
                checkOut,
                p.rooms() == null ? IntakeDefaults.ROOMS : p.rooms(),
                p.adults() == null ? IntakeDefaults.ADULTS : p.adults(),
                p.children() == null ? IntakeDefaults.CHILDREN : p.children(),
                email,
                clock.instant().plus(defaultDeadline),
                amenities(p.amenities()),
                AuthorizationMode.ENQUIRY_ONLY,
                maxBudget(p, checkIn, checkOut),
                false,
                prompt);

        if (!errors.isEmpty()) {
            // Report every problem at once: the user gets one chance to fix the prompt.
            try {
                validator.validate(command);
            } catch (RequestValidationException e) {
                throw merged(errors, e.errors());
            }
            throw new RequestValidationException(errors);
        }
        try {
            return submitTask.submit(command);
        } catch (RequestValidationException e) {
            throw merged(List.of(), e.errors());
        }
    }

    private static boolean isWeekend(DateExpression date) {
        return date.kind() == DateExpression.Kind.THIS_WEEKEND || date.kind() == DateExpression.Kind.NEXT_WEEKEND;
    }

    private static String singleEmail(String prompt, List<FieldError> errors) {
        Set<String> found = new LinkedHashSet<>();
        Matcher m = EMAIL.matcher(prompt);
        while (m.find()) {
            found.add(m.group().toLowerCase(Locale.ROOT));
        }
        if (found.isEmpty()) {
            errors.add(new FieldError("email", "Include the email address the result should be sent to"));
            return null;
        }
        if (found.size() > 1) {
            errors.add(new FieldError("email", "Include only one email address (found " + found.size() + ")"));
            return null;
        }
        return found.iterator().next();
    }

    /** A whole-stay budget wins; a nightly one is multiplied by the nights once the dates are known. */
    private static BigDecimal maxBudget(InterpretedPrompt p, LocalDate checkIn, LocalDate checkOut) {
        if (p.maxBudgetCad() != null || p.maxBudgetPerNightCad() == null || checkIn == null || checkOut == null) {
            return p.maxBudgetCad();
        }
        return p.maxBudgetPerNightCad().multiply(BigDecimal.valueOf(ChronoUnit.DAYS.between(checkIn, checkOut)));
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
        if (reported.contains("checkIn")) {
            reported.add("checkOut"); // without a check-in there is no check-out to complain about
        }
        for (FieldError e : validation) {
            String field = e.field() == null ? null : FIELD_NAMES.getOrDefault(e.field(), e.field());
            if (field == null || !reported.contains(field)) {
                all.add(new FieldError(field, e.message()));
            }
        }
        return new RequestValidationException(all);
    }
}
