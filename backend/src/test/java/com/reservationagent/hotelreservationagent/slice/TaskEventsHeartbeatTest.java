package com.reservationagent.hotelreservationagent.slice;

import com.reservationagent.hotelreservationagent.application.port.in.GetBookingTaskUseCase;
import com.reservationagent.hotelreservationagent.application.port.in.GetBookingTaskUseCase.TaskSnapshot;
import com.reservationagent.hotelreservationagent.application.port.in.SubmitBookingTaskUseCase;
import com.reservationagent.hotelreservationagent.application.port.in.SubmitTaskPromptUseCase;
import com.reservationagent.hotelreservationagent.application.port.in.SubscribeTaskProgressUseCase;
import com.reservationagent.hotelreservationagent.domain.task.BookingTask;
import com.reservationagent.hotelreservationagent.domain.task.TaskOutcome;
import com.reservationagent.hotelreservationagent.domain.task.TaskStatus;
import com.reservationagent.hotelreservationagent.presentation.rest.TaskController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.reservationagent.hotelreservationagent.support.Fixtures.task;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

/**
 * A task finished by the scheduler (watchdog, deadline) whose live event never reached this stream
 * is still delivered: the heartbeat re-reads the task, sends the terminal status and closes the stream.
 */
@WebMvcTest(TaskController.class)
@TestPropertySource(properties = "app.sse.heartbeat=50ms")
class TaskEventsHeartbeatTest {

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private SubmitBookingTaskUseCase submit;
    @MockitoBean
    private SubmitTaskPromptUseCase submitPrompt;
    @MockitoBean
    private GetBookingTaskUseCase getTask;
    @MockitoBean
    private SubscribeTaskProgressUseCase progress;

    private static BookingTask withStatus(BookingTask t, TaskStatus status, TaskOutcome outcome) {
        return new BookingTask(t.id(), t.requestId(), t.municipalityId(), t.stay(), t.destinationEmail(),
                t.authorizationMode(), t.maximumTotal(), t.requiredAmenities(), t.allowCompetingQuoteDisclosure(),
                t.intake(), t.deadline(), status, outcome, t.createdAt(), t.updatedAt().plusSeconds(
                        status == TaskStatus.COMPLETED ? 60 : 0));
    }

    private static TaskSnapshot snapshot(BookingTask t) {
        return new TaskSnapshot(t, "Ottawa", 0, 0, List.of(), null, null, Map.of(), null, null);
    }

    @Test
    void aTerminalStatusWithoutALiveEventStillArrivesAndClosesTheStream() throws Exception {
        BookingTask base = task();
        BookingTask calling = withStatus(base, TaskStatus.CALLING_FOR_QUOTES, null);
        BookingTask done = withStatus(base, TaskStatus.COMPLETED, TaskOutcome.NO_VALID_QUOTE);
        // Two reads while connecting, then the watchdog finishes the task; no live event is ever published.
        when(getTask.get(base.id())).thenReturn(Optional.of(snapshot(calling)), Optional.of(snapshot(calling)),
                Optional.of(snapshot(done)));
        AutoCloseable handle = mock(AutoCloseable.class);
        when(progress.subscribe(eq(base.id()), any())).thenReturn(handle);

        MvcResult result = mvc.perform(get("/api/tasks/{id}/events", base.id()).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(result.getResponse().getContentAsString()).contains("\"status\":\"COMPLETED\""));
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"message\":\"Calling hotels for availability and prices\"",
                "\"message\":\"Report email sent\"");
        verify(handle, timeout(2000)).close();
    }

    @Test
    void anIdleStreamGetsKeepAliveComments() throws Exception {
        BookingTask calling = withStatus(task(), TaskStatus.CALLING_FOR_QUOTES, null);
        when(getTask.get(calling.id())).thenReturn(Optional.of(snapshot(calling)));
        when(progress.subscribe(eq(calling.id()), any())).thenReturn(mock(AutoCloseable.class));

        MvcResult result = mvc.perform(get("/api/tasks/{id}/events", calling.id()).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(result.getResponse().getContentAsString()).contains(":keep-alive"));
    }
}
