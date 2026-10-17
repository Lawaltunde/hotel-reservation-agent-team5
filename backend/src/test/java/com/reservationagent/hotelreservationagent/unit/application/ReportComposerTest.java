package com.reservationagent.hotelreservationagent.unit.application;

import com.reservationagent.hotelreservationagent.application.port.out.AiUsageRepository;
import com.reservationagent.hotelreservationagent.application.port.out.EmailProvider.Email;
import com.reservationagent.hotelreservationagent.application.service.notification.ReportComposer;
import com.reservationagent.hotelreservationagent.application.service.notification.SentReport;
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

    @Test
    void everyReportEndsWithTheTeamSignOff() {
        Composed composed = composer.compose(input(TaskOutcome.NO_ELIGIBLE_HOTELS, null, null),
                new ReportContext(List.of(), USAGE));
        Composed fallback = composer.fallback(input(TaskOutcome.NO_ELIGIBLE_HOTELS, null, null));
        String expected = """
                —
                Team 5
                Hammed Lawal — lawalolatunde14@gmail.com
                Julia Bougaev — janojuly@gmail.com
                Aakash Uma Maheswaran — aakash.maheswaran@gmail.com
                """;

        assertThat(composed.email().textBody()).endsWith(expected);
        assertThat(composed.report().bodyText()).endsWith(expected);
        assertThat(fallback.email().textBody()).endsWith(expected);
        assertThat(fallback.report().bodyText()).endsWith(expected);
    }

    @Test
    void aMemberWithoutAnEmailIsListedByName() {
        ReportComposer custom = new ReportComposer(SUBJECT, UNCONFIRMED, "", null, "Team Five",
                "Ada Lovelace <ada@example.com>, Grace Hopper", EVALUATOR, CLOCK);
        String body = custom.compose(input(TaskOutcome.NO_ELIGIBLE_HOTELS, null, null),
                new ReportContext(List.of(), USAGE)).email().textBody();

        assertThat(body).endsWith("—\nTeam Five\nAda Lovelace — ada@example.com\nGrace Hopper\n");
    }

    private static com.reservationagent.hotelreservationagent.application.port.out.CallRecordingSource.CallRecording mp3(int bytes) {
        return new com.reservationagent.hotelreservationagent.application.port.out.CallRecordingSource.CallRecording(
                "audio/mpeg", new byte[bytes]);
    }

    @Test
    void recordingsAndTranscriptsAreAttachedChosenHotelFirst() {
        HotelOffer best = offer(task, rideau.id(), breakdown("300.00", "39.00", "12.00", "15.00"), NOW);
        CallEvidence other = evidence(task, budget, null);
        CallEvidence chosen = evidence(task, rideau, best);
        Composed composed = composer.compose(input(TaskOutcome.ENQUIRY_COMPLETED, best, rideau),
                new ReportContext(List.of(other, chosen), USAGE,
                        java.util.Map.of(other.call().id(), mp3(10), chosen.call().id(), mp3(20))));

        assertThat(composed.email().attachments()).extracting(a -> a.filename()).containsExactly(
                "call-1-rideau-inn.mp3", "call-1-rideau-inn-transcript.txt",
                "call-2-budget-motel.mp3", "call-2-budget-motel-transcript.txt");
        assertThat(composed.email().attachments().get(0).contentType()).isEqualTo("audio/mpeg");
        assertThat(new String(composed.email().attachments().get(1).content(), java.nio.charset.StandardCharsets.UTF_8))
                .isEqualTo("[00:00] Agent: Is a room available?\n[00:02] Hotel: Yes.\n");
        assertThat(composed.report().attachments()).extracting(SentReport.AttachmentInfo::fileName).containsExactly(
                "call-1-rideau-inn.mp3", "call-1-rideau-inn-transcript.txt",
                "call-2-budget-motel.mp3", "call-2-budget-motel-transcript.txt");
        assertThat(composed.report().attachments().get(0))
                .isEqualTo(new SentReport.AttachmentInfo("call-1-rideau-inn.mp3", "audio/mpeg", 20));
        assertThat(composed.email().textBody()).contains("Attachments: call-1-rideau-inn.mp3, ");
    }

    @Test
    void aMissingRecordingIsLinkedInstead() {
        HotelOffer best = offer(task, rideau.id(), breakdown("300.00", "39.00", "12.00", "15.00"), NOW);
        CallEvidence chosen = evidence(task, rideau, best);
        Composed composed = composer.compose(input(TaskOutcome.ENQUIRY_COMPLETED, best, rideau),
                new ReportContext(List.of(chosen), USAGE, java.util.Map.of()));

        assertThat(composed.email().attachments()).extracting(a -> a.filename())
                .containsExactly("call-1-rideau-inn-transcript.txt");
        assertThat(composed.email().textBody()).contains(
                "Rideau Inn: recording not yet available from ElevenLabs; link: https://agent.example.com/api/recordings/tok-");
    }

    @Test
    void recordingsStopAtTheSizeCapButTheChosenOneComesFirst() {
        HotelOffer best = offer(task, rideau.id(), breakdown("300.00", "39.00", "12.00", "15.00"), NOW);
        CallEvidence chosen = evidence(task, rideau, best);
        CallEvidence other = evidence(task, budget, null);
        int big = (int) (ReportComposer.MAX_ATTACHMENT_BYTES * 0.6);
        Composed composed = composer.compose(input(TaskOutcome.ENQUIRY_COMPLETED, best, rideau),
                new ReportContext(List.of(other, chosen), USAGE,
                        java.util.Map.of(other.call().id(), mp3(big), chosen.call().id(), mp3(big))));

        assertThat(composed.email().attachments()).extracting(a -> a.filename()).containsExactly(
                "call-1-rideau-inn.mp3", "call-1-rideau-inn-transcript.txt", "call-2-budget-motel-transcript.txt");
        assertThat(composed.email().attachments().stream().mapToLong(a -> a.content().length).sum())
                .isLessThanOrEqualTo(ReportComposer.MAX_ATTACHMENT_BYTES);
        assertThat(composed.email().textBody()).contains("Budget Motel: recording not attached (email size limit); link: ");
    }

    @Test
    void voicemailCallsAreNotAttached() {
        CallEvidence voicemail = evidence(task, budget, null);
        PhoneCall c = voicemail.call();
        CallEvidence asVoicemail = new CallEvidence(new PhoneCall(c.id(), c.taskId(), c.hotelId(), c.dialledNumber(),
                c.providerCallId(), c.conversationId(), CallStatus.COMPLETED, CallOutcome.VOICEMAIL, c.startedAt(),
                c.endedAt(), null), voicemail.hotel(), null, voicemail.transcript(), voicemail.recording(),
                voicemail.conversationUrl());
        Composed composed = composer.compose(input(TaskOutcome.NO_VALID_QUOTE, null, null),
                new ReportContext(List.of(asVoicemail), USAGE, java.util.Map.of(c.id(), mp3(10))));

        assertThat(composed.email().attachments()).isEmpty();
    }
}
