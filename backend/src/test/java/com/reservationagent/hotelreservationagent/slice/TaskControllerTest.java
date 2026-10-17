package com.reservationagent.hotelreservationagent.slice;

import com.reservationagent.hotelreservationagent.application.event.TaskProgressEvent;
import com.reservationagent.hotelreservationagent.application.port.in.GetBookingTaskUseCase;
import com.reservationagent.hotelreservationagent.application.port.in.GetBookingTaskUseCase.TaskSnapshot;
import com.reservationagent.hotelreservationagent.application.port.in.SubmitBookingTaskUseCase;
import com.reservationagent.hotelreservationagent.application.port.in.SubmitBookingTaskUseCase.Command;
import com.reservationagent.hotelreservationagent.application.port.in.SubscribeTaskProgressUseCase;
import com.reservationagent.hotelreservationagent.application.service.validation.OutOfScopeLocationException;
import com.reservationagent.hotelreservationagent.application.service.validation.RequestValidationException;
import com.reservationagent.hotelreservationagent.domain.hotel.Hotel;
import com.reservationagent.hotelreservationagent.domain.hotel.PriceTier;
import com.reservationagent.hotelreservationagent.domain.hotel.PropertyType;
import com.reservationagent.hotelreservationagent.domain.offer.HotelOffer;
import com.reservationagent.hotelreservationagent.domain.shared.DomainException;
import com.reservationagent.hotelreservationagent.domain.task.AuthorizationMode;
import com.reservationagent.hotelreservationagent.domain.task.BookingTask;
import com.reservationagent.hotelreservationagent.domain.task.TaskOutcome;
import com.reservationagent.hotelreservationagent.domain.task.TaskStatus;
import com.reservationagent.hotelreservationagent.presentation.rest.TaskController;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static com.reservationagent.hotelreservationagent.support.Fixtures.NOW;
import static com.reservationagent.hotelreservationagent.support.Fixtures.breakdown;
import static com.reservationagent.hotelreservationagent.support.Fixtures.hotel;
import static com.reservationagent.hotelreservationagent.support.Fixtures.offer;
import static com.reservationagent.hotelreservationagent.support.Fixtures.task;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskController.class)
class TaskControllerTest {

    private static final String VALID = """
            {"requestId": "  req-42  ", "city": "Ottawa", "checkIn": "2026-11-20", "checkOut": "2026-11-22",
             "rooms": 1, "adults": 2, "destinationEmail": "traveller@example.com",
             "deadline": "2026-11-10T18:00:00Z"}
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SubmitBookingTaskUseCase submit;
    @MockitoBean
    private GetBookingTaskUseCase getTask;
    @MockitoBean
    private SubscribeTaskProgressUseCase progress;

    private static TaskSnapshot snapshot(BookingTask task) {
        return new TaskSnapshot(task, "Ottawa", 0, 0, List.of(), null, null, Map.of());
    }

    private static BookingTask withStatus(BookingTask t, TaskStatus status, TaskOutcome outcome) {
        return new BookingTask(t.id(), t.requestId(), t.municipalityId(), t.stay(), t.destinationEmail(),
                t.authorizationMode(), t.maximumTotal(), t.requiredAmenities(), t.allowCompetingQuoteDisclosure(),
                t.deadline(), status, outcome, t.createdAt(), t.updatedAt());
    }

    // ---------- POST /api/tasks ----------

    @Test
    void acceptedTaskReturns202WithLocationAndStatus() throws Exception {
        BookingTask task = withStatus(task(), TaskStatus.VALIDATING, null);
        when(submit.submit(any())).thenReturn(task.id());
        when(getTask.get(task.id())).thenReturn(Optional.of(snapshot(task)));

        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/tasks/" + task.id()))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.taskId").value(task.id().toString()))
                .andExpect(jsonPath("$.status").value("VALIDATING"));

        ArgumentCaptor<Command> command = ArgumentCaptor.forClass(Command.class);
        verify(submit).submit(command.capture());
        assertThat(command.getValue()).satisfies(c -> {
            assertThat(c.requestId()).isEqualTo("req-42");
            assertThat(c.destinationEmail()).isEqualTo("traveller@example.com");
            assertThat(c.children()).isZero();
            assertThat(c.requiredAmenities()).isNull();
            assertThat(c.authorizationMode()).isNull();
            assertThat(c.maximumTotalCad()).isNull();
            assertThat(c.allowCompetingQuoteDisclosure()).isFalse();
            assertThat(c.deadline()).isEqualTo(java.time.Instant.parse("2026-11-10T18:00:00Z"));
        });
    }

    @Test
    void optionalFieldsArePassedThrough() throws Exception {
        BookingTask task = task();
        when(submit.submit(any())).thenReturn(task.id());
        when(getTask.get(task.id())).thenReturn(Optional.of(snapshot(task)));

        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content("""
                        {"requestId": "r", "city": "Ottawa", "checkIn": "2026-11-20", "checkOut": "2026-11-22",
                         "rooms": 2, "adults": 3, "children": 1, "destinationEmail": "a@example.com",
                         "deadline": "2026-11-10T18:00:00Z", "requiredAmenities": ["parking", "parking", "wifi"],
                         "authorizationMode": "REQUIRE_APPROVAL", "maximumTotalCad": 450.5,
                         "allowCompetingQuoteDisclosure": true}
                        """))
                .andExpect(status().isAccepted());

        ArgumentCaptor<Command> command = ArgumentCaptor.forClass(Command.class);
        verify(submit).submit(command.capture());
        assertThat(command.getValue()).satisfies(c -> {
            assertThat(c.rooms()).isEqualTo(2);
            assertThat(c.children()).isEqualTo(1);
            assertThat(c.requiredAmenities()).containsExactly("parking", "wifi");
            assertThat(c.authorizationMode()).isEqualTo(AuthorizationMode.REQUIRE_APPROVAL);
            assertThat(c.maximumTotalCad()).isEqualByComparingTo(new BigDecimal("450.50"));
            assertThat(c.allowCompetingQuoteDisclosure()).isTrue();
        });
    }

    @Test
    void missingFieldsGiveA400ProblemWithSortedFieldErrors() throws Exception {
        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.errors").value(org.hamcrest.Matchers.contains(
                        "adults: must not be null",
                        "checkIn: must not be null",
                        "checkOut: must not be null",
                        "city: must not be blank",
                        "deadline: must not be null",
                        "destinationEmail: must not be blank",
                        "requestId: must not be blank",
                        "rooms: must not be null")));
        verifyNoInteractions(submit);
    }

    @Test
    void structurallyInvalidValuesAreReportedPerField() throws Exception {
        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content(VALID
                        .replace("\"rooms\": 1", "\"rooms\": 0, \"children\": -1, \"maximumTotalCad\": -5")
                        .replace("traveller@example.com", "not-an-email")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").value(org.hamcrest.Matchers.contains(
                        "children: must be greater than or equal to 0",
                        "destinationEmail: must be a well-formed email address",
                        "maximumTotalCad: must be greater than 0",
                        "rooms: must be greater than or equal to 1")));
    }

    @Test
    void unknownAuthorizationModeNamesTheAllowedValues() throws Exception {
        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content(VALID.replace("\"rooms\": 1", "\"rooms\": 1, \"authorizationMode\": \"BOOK_NOW\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").value(org.hamcrest.Matchers.contains(
                        "authorizationMode: must be one of [ENQUIRY_ONLY, REQUIRE_APPROVAL, BOOK_IF_WITHIN_LIMITS]")));
    }

    @Test
    void malformedDateAndInstantAreExplained() throws Exception {
        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content(VALID.replace("2026-11-20", "20/11/2026")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("checkIn: must be a date in YYYY-MM-DD format"));

        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content(VALID.replace("2026-11-10T18:00:00Z", "tomorrow")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("deadline: must be an ISO-8601 instant, e.g. 2026-11-10T18:00:00Z"));
    }

    @Test
    void wrongTypeForANumberIsReportedAsInvalidValue() throws Exception {
        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content(VALID.replace("\"adults\": 2", "\"adults\": \"two\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("adults: has an invalid value"));
    }

    @Test
    void unreadableBodiesGiveA400Problem() throws Exception {
        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content("{\"city\": "))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors", hasSize(1)));

        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("Request body is missing or is not valid JSON"));
    }

    @Test
    void nonJsonContentTypeIsRejected() throws Exception {
        mvc.perform(post("/api/tasks").contentType(MediaType.TEXT_PLAIN).content("city=Ottawa"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void businessRuleViolationsBecome400WithErrors() throws Exception {
        when(submit.submit(any())).thenThrow(new RequestValidationException(
                List.of("checkIn is in the past", "deadline must be in the future")));

        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.detail").value("checkIn is in the past; deadline must be in the future"))
                .andExpect(jsonPath("$.errors").value(org.hamcrest.Matchers.contains(
                        "checkIn is in the past", "deadline must be in the future")));
    }

    @Test
    void domainExceptionBecomes400() throws Exception {
        when(submit.submit(any())).thenThrow(new DomainException("rooms and adults must be positive, children non-negative"));

        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("rooms and adults must be positive, children non-negative"));
    }

    @Test
    void cityOutsideOntarioIs422WithEmptySuggestions() throws Exception {
        when(submit.submit(any())).thenThrow(new OutOfScopeLocationException(
                "'Vancouver' is not a supported Ontario municipality", List.of()));

        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content(VALID.replace("Ottawa", "Vancouver")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.title").value("Location out of scope"))
                .andExpect(jsonPath("$.detail").value("'Vancouver' is not a supported Ontario municipality"))
                .andExpect(jsonPath("$.suggestions", hasSize(0)));
    }

    @Test
    void ambiguousCityIs422WithSuggestions() throws Exception {
        when(submit.submit(any())).thenThrow(new OutOfScopeLocationException(
                "'Springfield' matches several Ontario municipalities", List.of("Springwater", "Springfield")));

        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Ambiguous location"))
                .andExpect(jsonPath("$.suggestions").value(org.hamcrest.Matchers.contains("Springwater", "Springfield")));
    }

    // ---------- GET /api/tasks/{id} ----------

    @Test
    void unknownTaskIs404Problem() throws Exception {
        UUID id = UUID.randomUUID();
        when(getTask.get(id)).thenReturn(Optional.empty());

        mvc.perform(get("/api/tasks/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Task not found"))
                .andExpect(jsonPath("$.detail").value("No task with id " + id));
    }

    @Test
    void malformedTaskIdIs400() throws Exception {
        mvc.perform(get("/api/tasks/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(getTask);
    }

    @Test
    void finishedTaskWithoutOffersHasNullBestOfferAndNullUnknowns() throws Exception {
        BookingTask task = withStatus(task(), TaskStatus.COMPLETED, TaskOutcome.NO_ELIGIBLE_HOTELS);
        when(getTask.get(task.id())).thenReturn(Optional.of(snapshot(task)));

        mvc.perform(get("/api/tasks/{id}", task.id()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.taskId").value(task.id().toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.outcome").value("NO_ELIGIBLE_HOTELS"))
                .andExpect(jsonPath("$.city").value("Ottawa"))
                .andExpect(jsonPath("$.checkIn").value("2026-11-20"))
                .andExpect(jsonPath("$.checkOut").value("2026-11-22"))
                .andExpect(jsonPath("$.rooms").value(1))
                .andExpect(jsonPath("$.adults").value(2))
                .andExpect(jsonPath("$.children").value(0))
                .andExpect(jsonPath("$.authorizationMode").value("ENQUIRY_ONLY"))
                .andExpect(jsonPath("$.deadline").value(task.deadline().toString()))
                .andExpect(jsonPath("$.candidatesScreened").value(0))
                .andExpect(jsonPath("$.callsPlaced").value(0))
                .andExpect(jsonPath("$.bestOffer").value(nullValue()))
                .andExpect(jsonPath("$.offers", hasSize(0)))
                .andExpect(jsonPath("$.createdAt").value(NOW.toString()));
    }

    @Test
    void offersCarryHotelDetailsAndCadAmounts() throws Exception {
        BookingTask task = withStatus(task(), TaskStatus.COMPLETED, TaskOutcome.ENQUIRY_COMPLETED);
        Hotel hotel = hotel("Rideau Inn", PropertyType.HOTEL, 3, PriceTier.MIDSCALE, 0.9, "+16135550100");
        HotelOffer best = offer(task, hotel.id(), breakdown("300", "39", "12", "15"), NOW);
        when(getTask.get(task.id())).thenReturn(Optional.of(new TaskSnapshot(task, "Ottawa", 5, 2, List.of(best), best,
                null, Map.of(hotel.id(), hotel))));

        mvc.perform(get("/api/tasks/{id}", task.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidatesScreened").value(5))
                .andExpect(jsonPath("$.callsPlaced").value(2))
                .andExpect(jsonPath("$.offers", hasSize(1)))
                .andExpect(jsonPath("$.bestOffer.offerId").value(best.id().toString()))
                .andExpect(jsonPath("$.bestOffer.hotelId").value(hotel.id().toString()))
                .andExpect(jsonPath("$.bestOffer.hotelName").value("Rideau Inn"))
                .andExpect(jsonPath("$.bestOffer.hotelAddress").value("1 Main St, Ottawa, ON"))
                .andExpect(jsonPath("$.bestOffer.currency").value("CAD"))
                .andExpect(jsonPath("$.bestOffer.initialQuotedTotal").value(400.0))
                .andExpect(jsonPath("$.bestOffer.hst").value(39.0))
                .andExpect(jsonPath("$.bestOffer.enquiryId").value("ENQ-123"))
                .andExpect(jsonPath("$.bestOffer.quoteValidUntil").value(nullValue()))
                .andExpect(jsonPath("$.bestOffer.evidenceStatus").value("REFERENCE_ID_GIVEN"))
                .andExpect(jsonPath("$.offers[0].hotelName").value("Rideau Inn"));
    }

    // ---------- GET /api/tasks/{id}/events ----------

    @Test
    void eventsForAnUnknownTaskAre404() throws Exception {
        UUID id = UUID.randomUUID();
        when(getTask.get(id)).thenReturn(Optional.empty());

        mvc.perform(get("/api/tasks/{id}/events", id).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isNotFound());
        verifyNoInteractions(progress);
    }

    @Test
    void finishedTaskStreamsOneProgressEventAndCompletes() throws Exception {
        BookingTask task = withStatus(task(), TaskStatus.COMPLETED, TaskOutcome.NO_ELIGIBLE_HOTELS);
        when(getTask.get(task.id())).thenReturn(Optional.of(snapshot(task)));
        AutoCloseable handle = mock(AutoCloseable.class);
        when(progress.subscribe(eq(task.id()), any())).thenReturn(handle);

        MvcResult result = mvc.perform(get("/api/tasks/{id}/events", task.id()).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).startsWith("event:progress\ndata:")
                .contains("\"status\":\"COMPLETED\"", "\"message\":\"Current status\"", "\"taskId\":\"" + task.id() + "\"");
        assertThat(body.split("event:progress", -1)).hasSize(2);
        verify(handle).close();
    }

    @Test
    void runningTaskForwardsNewerEventsAndDropsStaleOnes() throws Exception {
        BookingTask running = withStatus(task(), TaskStatus.CALLING_FOR_QUOTES, null);
        when(getTask.get(running.id())).thenReturn(Optional.of(snapshot(running)));
        AtomicReference<Consumer<TaskProgressEvent>> listener = new AtomicReference<>();
        AutoCloseable handle = mock(AutoCloseable.class);
        when(progress.subscribe(eq(running.id()), any())).thenAnswer(inv -> {
            listener.set(inv.getArgument(1));
            return handle;
        });

        MvcResult result = mvc.perform(get("/api/tasks/{id}/events", running.id()).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        // Same status as the snapshot, and an event older than the snapshot: both already reflected.
        listener.get().accept(new TaskProgressEvent(running.id(), TaskStatus.CALLING_FOR_QUOTES, "dup", NOW.plusSeconds(1)));
        listener.get().accept(new TaskProgressEvent(running.id(), TaskStatus.RANKING, "stale", NOW.minusSeconds(1)));
        listener.get().accept(new TaskProgressEvent(running.id(), TaskStatus.NO_VALID_OFFER, "No comparable offer", NOW.plusSeconds(2)));
        listener.get().accept(new TaskProgressEvent(running.id(), TaskStatus.COMPLETED, "Report email accepted", NOW.plusSeconds(3)));

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"status\":\"CALLING_FOR_QUOTES\"", "No comparable offer", "Report email accepted")
                .doesNotContain("\"dup\"", "stale");
        assertThat(body.indexOf("No comparable offer")).isLessThan(body.indexOf("Report email accepted"));
        verify(handle).close();
    }
}
