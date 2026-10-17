package com.reservationagent.hotelreservationagent.unit.application;

import com.reservationagent.hotelreservationagent.application.port.out.AiUsageRepository;
import com.reservationagent.hotelreservationagent.application.service.notification.ReportComposer;
import com.reservationagent.hotelreservationagent.application.service.notification.ReportComposer.CallEvidence;
import com.reservationagent.hotelreservationagent.application.service.notification.ReportComposer.Composed;
import com.reservationagent.hotelreservationagent.application.service.notification.ReportComposer.ReportContext;
import com.reservationagent.hotelreservationagent.application.service.notification.ReportComposer.ReportInput;
import com.reservationagent.hotelreservationagent.application.service.recording.CallRecordingService.IssuedLink;
import com.reservationagent.hotelreservationagent.domain.call.CallOutcome;
import com.reservationagent.hotelreservationagent.domain.call.CallStatus;
import com.reservationagent.hotelreservationagent.domain.call.PhoneCall;
import com.reservationagent.hotelreservationagent.domain.hotel.Hotel;
import com.reservationagent.hotelreservationagent.domain.hotel.PriceTier;
import com.reservationagent.hotelreservationagent.domain.hotel.PropertyType;
import com.reservationagent.hotelreservationagent.domain.offer.HotelOffer;
import com.reservationagent.hotelreservationagent.domain.task.BookingTask;
import com.reservationagent.hotelreservationagent.domain.task.TaskOutcome;
import com.reservationagent.hotelreservationagent.domain.transcript.Transcript;
import com.reservationagent.hotelreservationagent.domain.transcript.TranscriptTurn;
import com.reservationagent.hotelreservationagent.domain.transcript.TranscriptTurn.Speaker;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static com.reservationagent.hotelreservationagent.support.Fixtures.CLOCK;
import static com.reservationagent.hotelreservationagent.support.Fixtures.NOW;
import static com.reservationagent.hotelreservationagent.support.Fixtures.breakdown;
import static com.reservationagent.hotelreservationagent.support.Fixtures.hotel;
import static com.reservationagent.hotelreservationagent.support.Fixtures.offer;
import static com.reservationagent.hotelreservationagent.support.Fixtures.task;
import static com.reservationagent.hotelreservationagent.support.Fixtures.unknownBreakdown;
import static org.assertj.core.api.Assertions.assertThat;

/** The one report email: the cheapest confirmed option, then evidence; a quote is never a booking. */
class ReportComposerTest {

    private static final String SUBJECT = "Team 5 - Hotel Reservation Enquiry Result";
    private static final AiUsageRepository.Totals USAGE = new AiUsageRepository.Totals(1200, 300, 95);

    private final ReportComposer composer = new ReportComposer(SUBJECT, "organizer@example.com",
            "Hotel Deal Agent <agent@example.com>", CLOCK);
    private final BookingTask task = task();
    private final Hotel rideau = hotel("Rideau Inn", PropertyType.HOTEL, 3, PriceTier.MIDSCALE, 0.9, "+16135550100");
    private final Hotel glebe = hotel("Glebe Motel", PropertyType.MOTEL, 2, PriceTier.ECONOMY, 0.9, "+16135550133");

    private PhoneCall call(Hotel h, CallOutcome outcome) {
        return new PhoneCall(UUID.randomUUID(), task.id(), h.id(), h.bestPhone(), "CA" + h.name().hashCode(),
                "conv_" + h.name().replace(' ', '_'), CallStatus.COMPLETED, outcome, NOW, NOW.plusSeconds(95), null);
    }

    private static Transcript transcript(PhoneCall c, String hotelLine) {
        return new Transcript(UUID.randomUUID(), c.id(), List.of(
                new TranscriptTurn(Speaker.AGENT, "Hi, I'm an AI assistant asking about a rate.", Duration.ZERO),
                new TranscriptTurn(Speaker.HOTEL, hotelLine, Duration.ofSeconds(5))), NOW);
    }

    private CallEvidence evidence(Hotel h, PhoneCall c, HotelOffer o, String hotelLine) {
        return new CallEvidence(c, h, o, transcript(c, hotelLine),
                new IssuedLink("https://agent.example.test/api/recordings/tok-" + c.conversationId(),
                        "https://agent.example.test/api/recordings/tok-" + c.conversationId()),
                "https://elevenlabs.io/app/conversational-ai/history/" + c.conversationId());
    }

    private Composed compose(TaskOutcome outcome, HotelOffer best, Hotel bestHotel, List<CallEvidence> calls) {
        return composer.compose(new ReportInput(task, "Ottawa", outcome, best, bestHotel, null, List.of(), 4, calls.size()),
                new ReportContext(calls, USAGE));
    }

    @Test
    void reportsTheChosenOfferInTheOrganizersFormatWithEvidence() {
        PhoneCall rideauCall = call(rideau, CallOutcome.OFFER_RECEIVED);
        HotelOffer best = offer(task, rideau.id(), breakdown("300.00", "39.00", "12.00", "15.00"), NOW);
        PhoneCall glebeCall = call(glebe, CallOutcome.NO_AVAILABILITY);

        Composed composed = compose(TaskOutcome.ENQUIRY_COMPLETED, best, rideau, List.of(
                evidence(rideau, rideauCall, best, "We can do 300 for both nights plus tax."),
                evidence(glebe, glebeCall, null, "Sorry, we're full.")));
        String body = composed.email().textBody();

        assertThat(composed.email().subject()).isEqualTo(SUBJECT);
        assertThat(composed.email().to()).isEqualTo("traveller@example.com");
        assertThat(composed.email().cc()).containsExactly("organizer@example.com");
        assertThat(body).contains(
                "Hotel Name: Rideau Inn",
                "Hotel Address: 1 Main St, Ottawa, ON",
                "Hotel Phone (number called): +16135550100",
                "Stay: 2026-11-20 to 2026-11-22 (2 nights)",
                "Total price for the stay: CAD 366.00 (all-in",
                "Reference #: ENQ-123",
                "Availability: confirmed by the hotel on the call",
                "No booking, reservation, hold or payment was made",
                " - Rideau Inn: available, CAD 366.00",
                " - Glebe Motel: NO_AVAILABILITY",
                "Tokens spent: 1500 (OpenAI prompt 1200 + completion 300); ElevenLabs call time: 95 s",
                "Call recording: https://agent.example.test/api/recordings/tok-conv_Rideau_Inn",
                "Conversation: https://elevenlabs.io/app/conversational-ai/history/conv_Rideau_Inn",
                "=== Rideau Inn (conversation conv_Rideau_Inn) ===",
                "Hotel: We can do 300 for both nights plus tax.",
                "=== Glebe Motel (conversation conv_Glebe_Motel) ===");
        assertThat(body.indexOf("Hotel Name:")).isLessThan(body.indexOf("Evidence"));
        assertThat(body).doesNotContain("Hotel Name: Glebe Motel");

        var fields = composed.report().fields();
        assertThat(fields.hotelName()).isEqualTo("Rideau Inn");
        assertThat(fields.hotelPhone()).isEqualTo("+16135550100");
        assertThat(fields.price()).isEqualByComparingTo("366.00");
        assertThat(fields.currency()).isEqualTo("CAD");
        assertThat(fields.reservationNumber()).isEqualTo("ENQ-123");
        assertThat(fields.recordingUrl()).endsWith("tok-conv_Rideau_Inn");
        assertThat(composed.report().bodyText()).isEqualTo(body);
        assertThat(composed.report().tokens().total()).isEqualTo(1500);
        assertThat(composed.report().transcripts()).hasSize(2);
    }

    @Test
    void withoutABestOfferPicksTheCheapestConfirmedPricedCall() {
        Hotel byward = hotel("ByWard Suites", PropertyType.HOTEL, 3, PriceTier.MIDSCALE, 0.9, "+16135550122");
        HotelOffer dearer = offer(task, rideau.id(), breakdown("300.00", "39.00", "12.00", "15.00"), NOW);
        HotelOffer cheaper = offer(task, byward.id(), breakdown("199.00", "25.87", "7.96", "15.00"), NOW);
        HotelOffer unpriced = offer(task, glebe.id(), unknownBreakdown(), NOW);

        Composed composed = compose(TaskOutcome.QUOTE_RECEIVED, null, null, List.of(
                evidence(rideau, call(rideau, CallOutcome.OFFER_RECEIVED), dearer, "366 all in"),
                evidence(glebe, call(glebe, CallOutcome.OFFER_RECEIVED), unpriced, "we have rooms"),
                evidence(byward, call(byward, CallOutcome.OFFER_RECEIVED), cheaper, "247.83 all in")));

        assertThat(composed.email().textBody()).contains("Hotel Name: ByWard Suites", "Total price for the stay: CAD 247.83");
        assertThat(composed.report().fields().price()).isEqualByComparingTo("247.83");
    }

    @Test
    void noConfirmedRoomSaysSoAndStillCarriesTheTranscripts() {
        PhoneCall glebeCall = call(glebe, CallOutcome.NO_AVAILABILITY);
        Composed composed = compose(TaskOutcome.NO_VALID_QUOTE, null, null,
                List.of(evidence(glebe, glebeCall, null, "Sorry, we're full.")));
        String body = composed.email().textBody();

        assertThat(body).contains("No confirmed available room was found among the 1 hotel(s) called.",
                "Stay asked for: 2026-11-20 to 2026-11-22 (2 nights)", "Hotel: Sorry, we're full.")
                .doesNotContain("Hotel Name:").doesNotContain("Call recording:");
        assertThat(composed.report().fields().hotelName()).isNull();
        assertThat(composed.report().fields().price()).isNull();
    }

    @Test
    void noCallsAtAllIsReportedHonestly() {
        String body = compose(TaskOutcome.NO_ELIGIBLE_HOTELS, null, null, List.of()).email().textBody();
        assertThat(body).contains("No confirmed available room was found among the 0 hotel(s) called.",
                "No call transcripts.");
    }

    @Test
    void organizerIsNotCopiedTwiceWhenItIsTheRecipient() {
        for (String organizer : List.of("Traveller@Example.com", "")) {
            ReportComposer c = new ReportComposer(SUBJECT, organizer, null, CLOCK);
            var email = c.compose(new ReportInput(task, "Ottawa", TaskOutcome.NO_ELIGIBLE_HOTELS, null, null, null,
                    List.of(), 0, 0), new ReportContext(List.of(), USAGE)).email();
            assertThat(email.cc()).as("organizer '%s'", organizer).isEmpty();
        }
    }
}
