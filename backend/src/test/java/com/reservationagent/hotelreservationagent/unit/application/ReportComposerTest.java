package com.reservationagent.hotelreservationagent.unit.application;

import com.reservationagent.hotelreservationagent.application.port.out.AiUsageRepository;
import com.reservationagent.hotelreservationagent.application.port.out.EmailProvider.Email;
import com.reservationagent.hotelreservationagent.application.service.notification.ReportComposer;
import com.reservationagent.hotelreservationagent.application.service.notification.ReportComposer.CallEvidence;
import com.reservationagent.hotelreservationagent.application.service.notification.ReportComposer.Composed;
import com.reservationagent.hotelreservationagent.application.service.notification.ReportComposer.ReportContext;
import com.reservationagent.hotelreservationagent.application.service.notification.ReportComposer.ReportInput;
import com.reservationagent.hotelreservationagent.application.service.pricing.OfferEvaluator;
import com.reservationagent.hotelreservationagent.application.service.recording.CallRecordingService.IssuedLink;
import com.reservationagent.hotelreservationagent.domain.call.CallOutcome;
import com.reservationagent.hotelreservationagent.domain.call.CallStatus;
import com.reservationagent.hotelreservationagent.domain.call.PhoneCall;
import com.reservationagent.hotelreservationagent.domain.hotel.Hotel;
import com.reservationagent.hotelreservationagent.domain.hotel.PriceTier;
import com.reservationagent.hotelreservationagent.domain.hotel.PropertyType;
import com.reservationagent.hotelreservationagent.domain.offer.EvidenceStatus;
import com.reservationagent.hotelreservationagent.domain.offer.HotelOffer;
import com.reservationagent.hotelreservationagent.domain.offer.PriceBreakdown;
import com.reservationagent.hotelreservationagent.domain.offer.TaxInclusion;
import com.reservationagent.hotelreservationagent.domain.shared.Money;
import com.reservationagent.hotelreservationagent.domain.task.BookingTask;
import com.reservationagent.hotelreservationagent.domain.task.TaskOutcome;
import com.reservationagent.hotelreservationagent.domain.transcript.Transcript;
import com.reservationagent.hotelreservationagent.domain.transcript.TranscriptTurn;
import com.reservationagent.hotelreservationagent.domain.transcript.TranscriptTurn.Speaker;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static com.reservationagent.hotelreservationagent.support.Fixtures.CLOCK;
import static com.reservationagent.hotelreservationagent.support.Fixtures.NOW;
import static com.reservationagent.hotelreservationagent.support.Fixtures.breakdown;
import static com.reservationagent.hotelreservationagent.support.Fixtures.hotel;
import static com.reservationagent.hotelreservationagent.support.Fixtures.offer;
import static com.reservationagent.hotelreservationagent.support.Fixtures.task;
import static org.assertj.core.api.Assertions.assertThat;

/** The single report email: cheapest confirmed option, honest wording, evidence inline. */
class ReportComposerTest {

    private static final String SUBJECT = "Team # 5 - Hotel Reservation Confirmed!";
    private static final String UNCONFIRMED = "Team # 5 - Hotel enquiry: no confirmed availability";
    private static final OfferEvaluator EVALUATOR = new OfferEvaluator(CLOCK, new BigDecimal("0.06"));
    private static final AiUsageRepository.Totals USAGE = new AiUsageRepository.Totals(1000, 200, 95);

    private final ReportComposer composer = new ReportComposer(SUBJECT, UNCONFIRMED, "organizer@example.org",
            "Hotel Deal Agent <agent@example.com>", EVALUATOR, CLOCK);
    private final BookingTask task = task();
    private final Hotel rideau = hotel("Rideau Inn", PropertyType.HOTEL, 3, PriceTier.MIDSCALE, 0.9, "+16135550100");
    private final Hotel budget = hotel("Budget Motel", PropertyType.MOTEL, 2, PriceTier.ECONOMY, 0.9, "+16135550111");

    private ReportInput input(TaskOutcome outcome, HotelOffer best, Hotel hotel) {
        return new ReportInput(task, "Ottawa", outcome, best, hotel, null, List.of(), 7, 2);
    }

    private static PhoneCall call(BookingTask task, Hotel hotel, UUID callId) {
        return new PhoneCall(callId, task.id(), hotel.id(), hotel.bestPhone(), "CA1", "conv_" + callId,
                CallStatus.COMPLETED, CallOutcome.OFFER_RECEIVED, NOW, NOW.plusSeconds(60), null);
    }

    private static CallEvidence evidence(BookingTask task, Hotel hotel, HotelOffer offer) {
        PhoneCall call = call(task, hotel, offer == null ? UUID.randomUUID() : offer.callId());
        Transcript transcript = new Transcript(UUID.randomUUID(), call.id(),
                List.of(new TranscriptTurn(Speaker.AGENT, "Is a room available?", Duration.ZERO),
                        new TranscriptTurn(Speaker.HOTEL, "Yes.", Duration.ofSeconds(2))), NOW);
        return new CallEvidence(call, hotel, offer, transcript,
                new IssuedLink("https://agent.example.com/api/recordings/tok-" + call.id(), "https://agent.example.com/api/recordings/tok-" + call.id()),
                "https://elevenlabs.io/app/conversational-ai/history/" + call.conversationId());
    }

    private static String line(Email email, String label) {
        return email.textBody().lines().filter(l -> l.startsWith(label + ":")).findFirst()
                .map(l -> l.substring(label.length() + 1).trim())
                .orElseThrow(() -> new AssertionError("no line '" + label + "' in\n" + email.textBody()));
    }

    @Test
    void reportsTheEvaluatorsPickWithEvidenceAndCopiesTheOrganizer() {
        HotelOffer best = offer(task, rideau.id(), breakdown("300.00", "39.00", "12.00", "15.00"), NOW);
        Composed composed = composer.compose(input(TaskOutcome.ENQUIRY_COMPLETED, best, rideau),
                new ReportContext(List.of(evidence(task, rideau, best)), USAGE));
        Email email = composed.email();

        assertThat(email.subject()).isEqualTo(SUBJECT);
        assertThat(email.to()).isEqualTo("traveller@example.com");
        assertThat(email.cc()).containsExactly("organizer@example.org");
        assertThat(line(email, "Hotel Name")).isEqualTo("Rideau Inn");
        assertThat(line(email, "Hotel Phone (number called)")).isEqualTo("+16135550100");
        assertThat(line(email, "Stay")).isEqualTo("2026-11-20 to 2026-11-22 (2 nights)");
        assertThat(line(email, "Price for the stay")).isEqualTo("366.00 CAD all-in, availability confirmed, not booked");
        assertThat(line(email, "Reservation #")).isEqualTo("ENQ-123");
        assertThat(email.textBody())
                .contains("No booking, reservation, hold or payment was made")
                .contains("Tokens spent: 1200 (OpenAI prompt 1000 + completion 200); ElevenLabs call time: 95 s")
                .contains("Agent: Is a room available?");
        assertThat(composed.report().fields().price()).isEqualByComparingTo("366.00");
        assertThat(composed.report().fields().conversationUrl()).startsWith("https://elevenlabs.io/");
        assertThat(composed.report().transcripts()).hasSize(1);
        assertThat(composed.report().bodyText()).isEqualTo(email.textBody());
    }

    @Test
    void anHstEstimateIsLabelledAndNeverShownAsAStatedTotal() {
        HotelOffer stated = new HotelOffer(UUID.randomUUID(), task.id(), budget.id(), UUID.randomUUID(), true,
                task.stay().checkIn(), task.stay().checkOut(), "Double", 2, null,
                new PriceBreakdown(Money.cad("250.00"), null, null, null, null, null, TaxInclusion.EXCLUDED),
                null, null, null, null, false, EvidenceStatus.VERBAL_ONLY, NOW);
        Composed composed = composer.compose(input(TaskOutcome.ENQUIRY_COMPLETED, stated, budget),
                new ReportContext(List.of(evidence(task, budget, stated)), USAGE));

        assertThat(composed.email().subject()).isEqualTo(SUBJECT);
        assertThat(line(composed.email(), "Hotel Name")).isEqualTo("Budget Motel");
        assertThat(line(composed.email(), "Price for the stay")).isEqualTo(
                "≈ CAD 282.50 incl. HST; municipal accommodation tax not confirmed (availability confirmed, not booked)");
        assertThat(line(composed.email(), "Reservation #")).isEqualTo("None provided (no reservation made, as instructed)");
        assertThat(composed.report().fields().reservationNumber()).isNull();
    }

    @Test
    void anUnavailableOfferIsNeverReportedAsTheOption() {
        HotelOffer soldOut = new HotelOffer(UUID.randomUUID(), task.id(), budget.id(), UUID.randomUUID(), false,
                task.stay().checkIn(), task.stay().checkOut(), null, 2, null,
                new PriceBreakdown(null, null, null, null, null, Money.cad("99.00"), TaxInclusion.INCLUDED),
                null, null, null, null, false, EvidenceStatus.VERBAL_ONLY, NOW);
        Composed composed = composer.compose(input(TaskOutcome.NO_VALID_QUOTE, null, null),
                new ReportContext(List.of(evidence(task, budget, soldOut)), USAGE));

        assertThat(composed.email().subject()).isEqualTo(UNCONFIRMED);
        assertThat(composed.email().textBody()).contains("No confirmed available room was found among the 1 hotel(s) called.");
        assertThat(composed.report().fields().hotelName()).isNull();
        assertThat(composed.report().fields().price()).isNull();
    }

    @Test
    void offerWithoutAnInitialQuoteStillComposes() {
        HotelOffer best = new HotelOffer(UUID.randomUUID(), task.id(), rideau.id(), UUID.randomUUID(), true,
                task.stay().checkIn(), task.stay().checkOut(), "Queen", 2, null,
                breakdown("300.00", "39.00", "12.00", "15.00"), null, null, null, null, false,
                EvidenceStatus.VERBAL_ONLY, NOW);
        Composed composed = composer.compose(input(TaskOutcome.ENQUIRY_COMPLETED, best, rideau),
                new ReportContext(List.of(evidence(task, rideau, best)), USAGE));

        assertThat(composed.report().fields().price()).isEqualByComparingTo(new BigDecimal("366.00"));
    }

    @Test
    void theOrganizerIsNotCopiedTwiceWhenItIsAlsoTheRecipient() {
        ReportComposer sameInbox = new ReportComposer(SUBJECT, UNCONFIRMED, "traveller@example.com", null, EVALUATOR, CLOCK);
        Composed composed = sameInbox.compose(input(TaskOutcome.NO_ELIGIBLE_HOTELS, null, null),
                new ReportContext(List.of(), USAGE));

        assertThat(composed.email().cc()).isEmpty();
        assertThat(composed.email().textBody()).contains("No call transcripts.");
    }
}
