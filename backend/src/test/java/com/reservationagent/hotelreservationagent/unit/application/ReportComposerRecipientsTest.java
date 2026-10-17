package com.reservationagent.hotelreservationagent.unit.application;

import com.reservationagent.hotelreservationagent.application.port.out.AiUsageRepository;
import com.reservationagent.hotelreservationagent.application.service.notification.ReportComposer;
import com.reservationagent.hotelreservationagent.application.service.notification.ReportComposer.Composed;
import com.reservationagent.hotelreservationagent.application.service.notification.ReportComposer.ReportContext;
import com.reservationagent.hotelreservationagent.application.service.notification.ReportComposer.ReportInput;
import com.reservationagent.hotelreservationagent.domain.task.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static com.reservationagent.hotelreservationagent.support.Fixtures.CLOCK;
import static com.reservationagent.hotelreservationagent.support.Fixtures.task;
import static org.assertj.core.api.Assertions.assertThat;

/** Who receives the report and under which subject; the body is covered by ReportComposerTest. */
class ReportComposerRecipientsTest {

    private static Composed compose(String subject, String organizer, String from) {
        return new ReportComposer(subject, organizer, from, CLOCK).compose(
                new ReportInput(task(), "Ottawa", TaskOutcome.NO_ELIGIBLE_HOTELS, null, null, null, List.of(), 0, 0),
                new ReportContext(List.of(), new AiUsageRepository.Totals(0, 0, 0)));
    }

    @Test
    void theConfiguredSubjectIsUsedForTheEmailTheStoredReportAndTheFirstLine() {
        Composed c = compose("Team 5 - Dry Run", "", null);
        assertThat(c.email().subject()).isEqualTo("Team 5 - Dry Run");
        assertThat(c.report().subject()).isEqualTo("Team 5 - Dry Run");
        assertThat(c.email().textBody()).startsWith("Team 5 - Dry Run\n");
    }

    @Test
    void theRequestersAddressIsTheRecipientAndTheOrganizerIsCopied() {
        Composed c = compose("S", "  organizer@example.org ", "Agent <agent@example.com>");
        assertThat(c.email().to()).isEqualTo("traveller@example.com");
        assertThat(c.email().cc()).containsExactly("organizer@example.org");
        assertThat(c.report().to()).isEqualTo("traveller@example.com");
        assertThat(c.report().cc()).containsExactly("organizer@example.org");
        assertThat(c.report().from()).isEqualTo("Agent <agent@example.com>");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "TRAVELLER@example.com"})
    void noCopyWithoutAnOrganizerOrWhenTheOrganizerIsTheRecipient(String organizer) {
        assertThat(compose("S", organizer, null).email().cc()).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void aBlankFromIsLeftToTheProvider(String from) {
        assertThat(compose("S", "", from).report().from()).isNull();
    }
}
