package com.reservationagent.hotelreservationagent.unit.application;

import com.reservationagent.hotelreservationagent.application.port.in.SubmitBookingTaskUseCase;
import com.reservationagent.hotelreservationagent.application.port.in.SubmitBookingTaskUseCase.Command;
import com.reservationagent.hotelreservationagent.application.port.out.AiUsageRepository;
import com.reservationagent.hotelreservationagent.application.port.out.AiUsageRepository.TokenUsage;
import com.reservationagent.hotelreservationagent.application.port.out.BookingTaskRepository;
import com.reservationagent.hotelreservationagent.application.port.out.TaskPromptInterpreter;
import com.reservationagent.hotelreservationagent.application.port.out.TaskPromptInterpreter.Budget;
import com.reservationagent.hotelreservationagent.application.port.out.TaskPromptInterpreter.BudgetUnit;
import com.reservationagent.hotelreservationagent.application.port.out.TaskPromptInterpreter.InterpretedPrompt;
import com.reservationagent.hotelreservationagent.application.port.out.TaskPromptInterpreter.Interpretation;
import com.reservationagent.hotelreservationagent.application.port.out.TaskPromptInterpreter.InterpreterUnavailableException;
import com.reservationagent.hotelreservationagent.application.service.intake.TaskPromptService;
import com.reservationagent.hotelreservationagent.application.service.validation.BookingRequestValidator;
import com.reservationagent.hotelreservationagent.application.service.validation.RequestValidationException;
import com.reservationagent.hotelreservationagent.application.service.validation.RequestValidationException.FieldError;
import com.reservationagent.hotelreservationagent.domain.shared.Money;
import com.reservationagent.hotelreservationagent.domain.task.AuthorizationMode;
import com.reservationagent.hotelreservationagent.domain.task.DateExpression;
import com.reservationagent.hotelreservationagent.domain.task.DateExpression.Kind;
import com.reservationagent.hotelreservationagent.domain.task.TaskIntake.EmailSource;
import com.reservationagent.hotelreservationagent.domain.task.TaskIntake.StatedField;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.reservationagent.hotelreservationagent.support.Fixtures.task;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** One free-text prompt to one task: Java owns the email, the dates, the defaults and the budget. */
class TaskPromptServiceTest {

    /** Wednesday 2026-11-04, 10:00 in Toronto. */
    private static final Instant NOW = Instant.parse("2026-11-04T15:00:00Z");
    private static final LocalDate TODAY = LocalDate.parse("2026-11-04");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final TokenUsage TOKENS = new TokenUsage(400, 80);

    private final TaskPromptInterpreter interpreter = mock(TaskPromptInterpreter.class);
    private final SubmitBookingTaskUseCase submitTask = mock(SubmitBookingTaskUseCase.class);
    private final BookingRequestValidator validator = mock(BookingRequestValidator.class);
    private final BookingTaskRepository tasks = mock(BookingTaskRepository.class);
    private final AiUsageRepository usage = mock(AiUsageRepository.class);
    private final UUID taskId = UUID.randomUUID();

    private TaskPromptService service(String organizer) {
        return new TaskPromptService(interpreter, submitTask, validator, tasks, CLOCK, Duration.ofMinutes(10),
                organizer, usage);
    }

    private final TaskPromptService service = service("");

    @BeforeEach
    void defaults() {
        when(tasks.findByRequestId(anyString())).thenReturn(Optional.empty());
        when(submitTask.submit(any())).thenReturn(taskId);
        interprets(prompt("Ottawa", null, null, null, null, null, null, null));
    }

    private void interprets(InterpretedPrompt p) {
        when(interpreter.interpret(anyString())).thenReturn(new Interpretation(p, TOKENS));
    }

    private static InterpretedPrompt prompt(String city, DateExpression in, DateExpression out, Integer nights,
                                            Integer rooms, Integer adults, Integer children, Budget budget) {
        return new InterpretedPrompt(city, null, in, out, nights, rooms, adults, children, budget, List.of(), null);
    }

    private static DateExpression kind(Kind kind) {
        return new DateExpression(kind, null, null, null, null, null);
    }

    private Command submitted(String text) {
        assertThat(service.submit(text, null)).isEqualTo(taskId);
        ArgumentCaptor<Command> command = ArgumentCaptor.forClass(Command.class);
        verify(submitTask).submit(command.capture());
        return command.getValue();
    }

    @Test
    void emailIsFoundByJavaLowerCasedAndRedactedBeforeTheModel() {
        Command c = submitted("Cheapest hotel in Ottawa tonight. Send it to Jane.Doe+trips@Example.CA please");

        assertThat(c.destinationEmail()).isEqualTo("jane.doe+trips@example.ca");
        assertThat(c.intake().emailSource()).isEqualTo(EmailSource.STATED);
        assertThat(c.intake().isStated(StatedField.EMAIL)).isTrue();
        verify(interpreter).interpret("Cheapest hotel in Ottawa tonight. Send it to <email> please");
    }

    @Test
    void withSeveralAddressesTheLatestWinsAndAllAreRedacted() {
        Command c = submitted("email old@example.com\nactually use new@example.com");

        assertThat(c.destinationEmail()).isEqualTo("new@example.com");
        verify(interpreter).interpret("email <email>\nactually use <email>");
    }

    @Test
    void noEmailAndNoOrganizerInboxIs400OnTheEmailField() {
        assertThatExceptionOfType(RequestValidationException.class)
                .isThrownBy(() -> service.submit("cheapest hotel in Ottawa tonight", null))
                .satisfies(e -> assertThat(e.errors()).first()
                        .isEqualTo(new FieldError("email", "Include the email address the result should be sent to")));
        verify(submitTask, never()).submit(any());
    }

    @Test
    void noEmailFallsBackToTheOrganizerInboxAndSaysSo() {
        TaskPromptService withOrganizer = service(" Organizer@Example.com ");
        withOrganizer.submit("cheapest hotel in Ottawa tonight", null);

        ArgumentCaptor<Command> command = ArgumentCaptor.forClass(Command.class);
        verify(submitTask).submit(command.capture());
        Command c = command.getValue();
        assertThat(c.destinationEmail()).isEqualTo("organizer@example.com");
        assertThat(c.intake().emailSource()).isEqualTo(EmailSource.ORGANIZER_FALLBACK);
        assertThat(c.intake().isStated(StatedField.EMAIL)).isFalse();
        assertThat(c.intake().assumptions()).anyMatch(a -> a.contains("organizer inbox"));
    }

    @Test
    void unstatedFieldsGetSearchDefaultsButAreNotMarkedStated() {
        Command c = submitted("hotel in Ottawa, a@example.com");

        assertThat(c.city()).isEqualTo("Ottawa");
        assertThat(c.checkIn()).isEqualTo(TODAY);
        assertThat(c.checkOut()).isEqualTo(TODAY.plusDays(1));
        assertThat(c.rooms()).isEqualTo(1);
        assertThat(c.adults()).isEqualTo(1);
        assertThat(c.children()).isZero();
        assertThat(c.maximumTotalCad()).isNull();
        assertThat(c.authorizationMode()).isEqualTo(AuthorizationMode.ENQUIRY_ONLY);
        assertThat(c.deadline()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        assertThat(c.requestId()).isNotBlank();
        assertThat(c.intake().stated()).containsExactly(StatedField.EMAIL);
        assertThat(c.intake().originalPrompt()).isEqualTo("hotel in Ottawa, a@example.com");
        assertThat(c.intake().assumptions()).hasSize(3)
                .anyMatch(a -> a.contains("tonight (2026-11-04)"))
                .anyMatch(a -> a.contains("1 night"))
                .anyMatch(a -> a.contains("guests not given"));
    }

    @Test
    void statedValuesAreUsedAndMarked() {
        interprets(prompt("Kingston", kind(Kind.TOMORROW), null, 3, 2, 4, 1, null));
        Command c = submitted("Kingston tmrw 3 nights 2 rooms 4 adults 1 kid, a@example.com");

        assertThat(c.checkIn()).isEqualTo(TODAY.plusDays(1));
        assertThat(c.checkOut()).isEqualTo(TODAY.plusDays(4));
        assertThat(c.rooms()).isEqualTo(2);
        assertThat(c.adults()).isEqualTo(4);
        assertThat(c.children()).isEqualTo(1);
        assertThat(c.intake().stated()).containsExactlyInAnyOrder(StatedField.EMAIL, StatedField.CHECK_IN,
                StatedField.CHECK_OUT, StatedField.ROOMS, StatedField.ADULTS, StatedField.CHILDREN);
        assertThat(c.intake().assumptions()).isEmpty();
    }

    @Test
    void aWeekendWithNoLengthMeansFridayAndSaturdayNights() {
        interprets(prompt("Ottawa", kind(Kind.THIS_WEEKEND), null, null, null, 2, null, null));
        Command c = submitted("Ottawa this weekend for 2, a@example.com");

        assertThat(c.checkIn()).isEqualTo(LocalDate.parse("2026-11-06"));
        assertThat(c.checkOut()).isEqualTo(LocalDate.parse("2026-11-08"));
        assertThat(c.intake().assumptions()).anyMatch(a -> a.contains("2 nights"));
    }

    @Test
    void anExplicitCheckOutBeatsTheLength() {
        interprets(prompt("Ottawa", kind(Kind.TOMORROW),
                new DateExpression(Kind.THIS_WEEKDAY, null, null, null, java.time.DayOfWeek.SUNDAY, null), 5, null, 2, null, null));
        Command c = submitted("Ottawa tomorrow until Sunday, a@example.com");

        assertThat(c.checkIn()).isEqualTo(LocalDate.parse("2026-11-05"));
        assertThat(c.checkOut()).isEqualTo(LocalDate.parse("2026-11-08"));
    }

    @Test
    void anUnresolvableDateIsIgnoredAndNoted() {
        interprets(prompt("Ottawa", new DateExpression(Kind.ABSOLUTE, null, 2, 30, null, null), null, null, null, 2, null, null));
        Command c = submitted("Ottawa on Feb 30, a@example.com");

        assertThat(c.checkIn()).isEqualTo(TODAY);
        assertThat(c.intake().isStated(StatedField.CHECK_IN)).isFalse();
        assertThat(c.intake().assumptions()).anyMatch(a -> a.contains("check-in date could not be understood"));
    }

    @Test
    void aNightlyBudgetIsMultipliedByTheNights() {
        interprets(prompt("Ottawa", kind(Kind.TOMORROW), null, 2, null, 2, null,
                new Budget(new BigDecimal("180"), BudgetUnit.PER_NIGHT)));
        Command c = submitted("Ottawa tmrw 2 nights max $180/night, a@example.com");

        assertThat(c.maximumTotalCad()).isEqualByComparingTo("360");
        assertThat(c.intake().maximumPerNight()).isEqualTo(Money.cad("180"));
        assertThat(c.intake().isStated(StatedField.MAX_BUDGET)).isTrue();
    }

    @Test
    void aStayBudgetIsUsedAsGiven() {
        interprets(prompt("Ottawa", kind(Kind.TOMORROW), null, 2, null, 2, null,
                new Budget(new BigDecimal("400"), BudgetUnit.PER_STAY)));
        Command c = submitted("Ottawa tmrw 2 nights $400 total, a@example.com");

        assertThat(c.maximumTotalCad()).isEqualByComparingTo("400");
        assertThat(c.intake().maximumPerNight()).isNull();
    }

    @Test
    void anAmbiguousBudgetIsPerNightWhenSmallAndTheStayIsLonger() {
        interprets(prompt("Ottawa", kind(Kind.TOMORROW), null, 3, null, 2, null,
                new Budget(new BigDecimal("150"), BudgetUnit.UNKNOWN)));
        assertThat(submitted("Ottawa 3 nights under $150, a@example.com").maximumTotalCad()).isEqualByComparingTo("450");
    }

    @Test
    void anAmbiguousLargeBudgetIsTheStayTotal() {
        interprets(prompt("Ottawa", kind(Kind.TOMORROW), null, 3, null, 2, null,
                new Budget(new BigDecimal("700"), BudgetUnit.UNKNOWN)));
        assertThat(submitted("Ottawa 3 nights under $700, a@example.com").maximumTotalCad()).isEqualByComparingTo("700");
    }

    @Test
    void anIdempotencyKeyReplayReturnsTheTaskWithoutCallingTheModel() {
        when(tasks.findByRequestId("prompt:chat-42")).thenReturn(Optional.of(task()));
        UUID existing = service.submit("anything, a@example.com", " chat-42 ");

        assertThat(existing).isNotNull();
        verifyNoInteractions(interpreter, submitTask, usage);
    }

    @Test
    void theIdempotencyKeyBecomesTheRequestId() {
        service.submit("Ottawa, a@example.com", "chat-43");
        ArgumentCaptor<Command> command = ArgumentCaptor.forClass(Command.class);
        verify(submitTask).submit(command.capture());
        assertThat(command.getValue().requestId()).isEqualTo("prompt:chat-43");
    }

    @Test
    void anOverlongIdempotencyKeyIs400() {
        assertThatExceptionOfType(RequestValidationException.class)
                .isThrownBy(() -> service.submit("Ottawa, a@example.com", "k".repeat(129)))
                .satisfies(e -> assertThat(e.errors()).extracting(FieldError::field).containsExactly("Idempotency-Key"));
        verifyNoInteractions(interpreter);
    }

    @Test
    void validationErrorsUseThisEndpointsFieldNames() {
        when(submitTask.submit(any())).thenThrow(new RequestValidationException(List.of(
                new FieldError("destinationEmail", "A valid email address is required"),
                new FieldError("maximumTotalCad", "Budget must be positive"),
                new FieldError("checkIn", "Check-in date is in the past"))));

        assertThatExceptionOfType(RequestValidationException.class)
                .isThrownBy(() -> service.submit("Ottawa, a@example.com", null))
                .satisfies(e -> assertThat(e.errors()).extracting(FieldError::field)
                        .containsExactly("email", "maxBudget", "checkIn"));
    }

    @Test
    void tokensAreRecordedForTheNewTask() {
        service.submit("Ottawa, a@example.com", null);
        verify(usage).record(eq(taskId), eq("prompt-intake"), eq(TOKENS), eq(0L));
    }

    @Test
    void anUnavailableModelPropagatesSoTheApiAnswers503() {
        when(interpreter.interpret(anyString())).thenThrow(new InterpreterUnavailableException("down", null));

        assertThatExceptionOfType(InterpreterUnavailableException.class)
                .isThrownBy(() -> service.submit("Ottawa, a@example.com", null));
        verify(submitTask, never()).submit(any());
    }
}
