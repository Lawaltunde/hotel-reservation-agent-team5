package com.reservationagent.hotelreservationagent.application.service.call;

import com.reservationagent.hotelreservationagent.domain.task.BookingTask;
import com.reservationagent.hotelreservationagent.domain.task.StayDetails;
import com.reservationagent.hotelreservationagent.domain.task.TaskIntake;
import com.reservationagent.hotelreservationagent.domain.task.TaskIntake.StatedField;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.reservationagent.hotelreservationagent.support.Fixtures.task;
import static org.assertj.core.api.Assertions.assertThat;

/** What the voice agent says: a natural date phrase, and guests only as the guest stated them. */
class CallSchedulerPhrasesTest {

    private static final LocalDate SAT = LocalDate.parse("2026-10-17");

    private static BookingTask stay(LocalDate in, LocalDate out, int rooms, int adults, int children,
                                    Set<StatedField> stated) {
        BookingTask t = task();
        return new BookingTask(t.id(), t.requestId(), t.municipalityId(), new StayDetails(in, out, rooms, adults, children),
                t.destinationEmail(), t.authorizationMode(), null, Set.of(), false,
                new TaskIntake("prompt", stated, TaskIntake.EmailSource.STATED, null, null, List.of()),
                t.deadline(), t.status(), null, t.createdAt(), t.updatedAt());
    }

    @Test
    void tonightForOneNightReadsNaturally() {
        BookingTask t = stay(SAT, SAT.plusDays(1), 1, 1, 0, EnumSet.noneOf(StatedField.class));
        assertThat(CallScheduler.stayRequest(t, SAT))
                .isEqualTo("tonight, Saturday October 17, for one night, checking out Sunday October 18");
        assertThat(CallScheduler.party(t)).isEqualTo(CallScheduler.NO_GUEST_COUNT);
    }

    @Test
    void laterStaysNameTheDayAndNights() {
        BookingTask t = stay(SAT.plusDays(6), SAT.plusDays(8), 2, 4, 1,
                EnumSet.of(StatedField.CHECK_IN, StatedField.ROOMS, StatedField.ADULTS, StatedField.CHILDREN));
        assertThat(CallScheduler.stayRequest(t, SAT))
                .isEqualTo("Friday October 23, for 2 nights, checking out Sunday October 25");
        assertThat(CallScheduler.party(t)).isEqualTo("2 rooms, 4 adults and 1 child");
    }

    @Test
    void tomorrowIsTomorrowNight() {
        BookingTask t = stay(SAT.plusDays(1), SAT.plusDays(2), 1, 2, 0, EnumSet.of(StatedField.ADULTS));
        assertThat(CallScheduler.stayRequest(t, SAT)).startsWith("tomorrow night, Sunday October 18, for one night");
        assertThat(CallScheduler.party(t)).isEqualTo("2 adults");
    }
}
