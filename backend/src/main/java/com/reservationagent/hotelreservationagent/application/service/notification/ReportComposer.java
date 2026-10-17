package com.reservationagent.hotelreservationagent.application.service.notification;

import com.reservationagent.hotelreservationagent.application.port.out.AiUsageRepository;
import com.reservationagent.hotelreservationagent.application.port.out.EmailProvider.Email;
import com.reservationagent.hotelreservationagent.application.service.recording.CallRecordingService.IssuedLink;
import com.reservationagent.hotelreservationagent.domain.call.PhoneCall;
import com.reservationagent.hotelreservationagent.domain.hotel.Hotel;
import com.reservationagent.hotelreservationagent.domain.offer.HotelOffer;
import com.reservationagent.hotelreservationagent.domain.offer.OfferPrice;
import com.reservationagent.hotelreservationagent.application.service.pricing.OfferEvaluator;
import com.reservationagent.hotelreservationagent.domain.reservation.Reservation;
import com.reservationagent.hotelreservationagent.domain.shared.Money;
import com.reservationagent.hotelreservationagent.domain.task.BookingTask;
import com.reservationagent.hotelreservationagent.domain.task.StayDetails;
import com.reservationagent.hotelreservationagent.domain.task.TaskOutcome;
import com.reservationagent.hotelreservationagent.domain.transcript.Transcript;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Builds the single report email in the organizer's format: the cheapest confirmed-available
 * option (hotel, address, number called, stay dates, total price, reference # if given), then the
 * evidence (tokens spent, recording and conversation links, transcripts inline). Every quote is
 * reported as a quote: nothing is booked, held or paid.
 */
@Component
public class ReportComposer {

    private static final String UNKNOWN = "Unknown";
    public static final String DEFAULT_SUBJECT = "Team # 5 - Hotel Reservation Confirmed!";
    public static final String DEFAULT_SUBJECT_UNCONFIRMED = "Team # 5 - Hotel enquiry: no confirmed availability";
    /** The body never repeats the subject: the body states plainly that nothing was booked. */
    static final String HEADING = "Team 5 - hotel enquiry result";
    static final String NO_RESERVATION = "None provided (no reservation made, as instructed)";
    public static final String CURRENCY = Money.CURRENCY;

    private final String subject;
    private final String subjectUnconfirmed;
    private final String reportRecipient;
    private final OfferEvaluator evaluator;
    private final String from;
    private final Clock clock;

    public ReportComposer(@Value("${app.report.subject:" + DEFAULT_SUBJECT + "}") String subject,
                          @Value("${app.report.subject-unconfirmed:" + DEFAULT_SUBJECT_UNCONFIRMED + "}") String subjectUnconfirmed,
                          @Value("${app.report.recipient:}") String reportRecipient,
                          @Value("${hotel-agent.resend.from:}") String from,
                          OfferEvaluator evaluator,
                          Clock clock) {
        this.subject = subject;
        this.subjectUnconfirmed = subjectUnconfirmed;
        this.evaluator = evaluator;
        this.reportRecipient = reportRecipient == null ? "" : reportRecipient.strip();
        this.from = from == null || from.isBlank() ? null : from.strip();
        this.clock = clock;
    }

    public record ReportInput(BookingTask task, String city, TaskOutcome outcome, HotelOffer bestOffer, Hotel hotel,
                              Reservation reservation, List<Transcript> transcripts, int screened, int called) {
    }

    /** Everything about the calls that the report shows as evidence. */
    public record ReportContext(List<CallEvidence> calls, AiUsageRepository.Totals usage) {
    }

    /** One call: who was called, what they quoted (if anything), and links to the recording. */
    public record CallEvidence(PhoneCall call, Hotel hotel, HotelOffer offer, Transcript transcript, IssuedLink recording,
                               String conversationUrl) {
    }

    /** Who gets the report. */
    public record Recipients(String to, List<String> cc) {
    }

    public record Composed(Email email, SentReport report) {
    }

    public Composed compose(ReportInput in, ReportContext ctx) {
        BookingTask t = in.task();
        Optional<CallEvidence> chosen = chosen(in, ctx);
        HotelOffer offer = chosen.map(CallEvidence::offer).orElse(null);
        Hotel hotel = chosen.map(CallEvidence::hotel).orElse(null);
        OfferPrice price = offer == null ? null : evaluator.price(offer).orElse(null);
        String subject = subject(chosen.isPresent());
        StayDetails stay = t.stay();

        StringBuilder b = new StringBuilder();
        b.append(HEADING).append("\n\n");
        if (chosen.isPresent()) {
            b.append("Cheapest confirmed available option among the hotels called:\n\n");
            b.append("Hotel Name: ").append(hotel == null ? UNKNOWN : hotel.name()).append('\n');
            b.append("Hotel Address: ").append(hotel == null ? UNKNOWN : orUnknown(hotel.address())).append('\n');
            b.append("Hotel Phone (number called): ").append(chosen.get().call().dialledNumber().e164()).append('\n');
            b.append("Stay: ").append(stayText(stay)).append('\n');
            b.append("Price for the stay: ").append(priceText(price)).append('\n');
            b.append("Reservation #: ").append(reference(offer) == null ? NO_RESERVATION : reference(offer)).append('\n');
        } else {
            b.append("No confirmed available room was found among the ").append(ctx.calls().size())
                    .append(" hotel(s) called.\n");
            b.append("Stay asked for: ").append(stayText(stay)).append('\n');
        }
        b.append("\nThis is an enquiry result only. No booking, reservation, hold or payment was made; ")
                .append("the hotel was told we would confirm later.\n");

        if (t.intake().originalPrompt() != null) {
            b.append("\nRequest: \"").append(t.intake().originalPrompt().strip()).append("\"\n");
        }
        if (!t.intake().assumptions().isEmpty()) {
            b.append("Assumptions: ").append(String.join("; ", t.intake().assumptions())).append('\n');
        }

        b.append("\nHotels called (").append(ctx.calls().size()).append(", ").append(in.screened())
                .append(" screened):\n");
        for (CallEvidence c : ctx.calls()) {
            b.append(" - ").append(c.hotel() == null ? UNKNOWN : c.hotel().name()).append(": ").append(callSummary(c))
                    .append('\n');
        }

        AiUsageRepository.Totals usage = ctx.usage();
        b.append("\nEvidence\n");
        b.append("Tokens spent: ").append(usage.tokens()).append(" (OpenAI prompt ").append(usage.promptTokens())
                .append(" + completion ").append(usage.completionTokens()).append("); ElevenLabs call time: ")
                .append(usage.callSeconds()).append(" s\n");
        chosen.ifPresent(c -> {
            b.append("Call recording: ").append(c.recording().text()).append('\n');
            b.append("Conversation: ").append(c.conversationUrl() == null ? "Unavailable" : c.conversationUrl()).append('\n');
        });

        List<SentReport.TranscriptEntry> transcripts = ctx.calls().stream()
                .filter(c -> c.transcript() != null)
                .map(c -> new SentReport.TranscriptEntry(c.hotel() == null ? UNKNOWN : c.hotel().name(),
                        c.call().conversationId(), c.conversationUrl(), c.recording().url(), c.call().startedAt(),
                        c.transcript().asPlainText()))
                .toList();
        b.append("\nCall transcripts\n");
        if (transcripts.isEmpty()) {
            b.append("No call transcripts.\n");
        }
        for (SentReport.TranscriptEntry e : transcripts) {
            b.append("\n=== ").append(e.hotelName()).append(" (conversation ").append(e.conversationId()).append(") ===\n");
            b.append("Recording: ").append(e.recordingUrl() == null ? "Unavailable" : e.recordingUrl()).append('\n');
            b.append("Conversation: ").append(e.conversationUrl() == null ? "Unavailable" : e.conversationUrl()).append('\n');
            b.append(e.text()).append('\n');
        }
        b.append("\nHotel data: Google Places, OpenStreetMap contributors.\n\nHotel Deal Agent (Team 5)\n");

        Recipients recipients = recipients(t);
        String body = b.toString();
        Email email = new Email(recipients.to(), recipients.cc(), subject, body, List.of());
        SentReport.Fields fields = new SentReport.Fields(
                hotel == null ? null : hotel.name(),
                hotel == null ? null : hotel.address(),
                chosen.map(c -> c.call().dialledNumber().e164()).orElse(null),
                offer == null ? null : reference(offer),
                stay.checkIn(), stay.checkOut(),
                price == null ? null : price.amount().amount(), CURRENCY,
                price == null ? null : priceText(price),
                chosen.map(c -> c.recording().url()).orElse(null),
                chosen.map(CallEvidence::conversationUrl).orElse(null));
        SentReport report = new SentReport(subject, from, recipients.to(), recipients.cc(), body, null, fields,
                new SentReport.Tokens(usage.promptTokens(), usage.completionTokens(), usage.callSeconds()),
                transcripts, clock.instant());
        return new Composed(email, report);
    }

    /** A minimal report that relies on as little as possible; used when {@link #compose} fails. */
    public Composed fallback(ReportInput in) {
        BookingTask t = in.task();
        StringBuilder body = new StringBuilder(HEADING).append("\n\n");
        body.append("Status: ").append(in.outcome()).append('\n');
        body.append("Stay: ").append(t.stay().checkIn()).append(" to ").append(t.stay().checkOut()).append('\n');
        if (in.hotel() != null) {
            body.append("Hotel Name: ").append(in.hotel().name()).append('\n');
            body.append("Hotel Address: ").append(orUnknown(in.hotel().address())).append('\n');
            body.append("Hotel Phone: ").append(in.hotel().bestPhone() == null ? UNKNOWN : in.hotel().bestPhone().e164()).append('\n');
        }
        if (in.bestOffer() != null) {
            body.append("Price for the stay: ").append(priceText(evaluator.price(in.bestOffer()).orElse(null))).append('\n');
        }
        body.append("\nSome report details could not be produced. No booking, reservation, hold or payment was made.\n");
        Recipients recipients = recipients(t);
        Email email = new Email(recipients.to(), recipients.cc(), subject(false), body.toString(), List.of());
        SentReport report = new SentReport(subject(false), from, recipients.to(), recipients.cc(), body.toString(), null,
                null, null, List.of(), clock.instant());
        return new Composed(email, report);
    }

    /**
     * {@code app.report.subject} when the report names an option whose availability the hotel
     * confirmed, else {@code app.report.subject-unconfirmed}. Wording is config-only; this is the one
     * place that chooses between them.
     */
    String subject(boolean confirmedOption) {
        return confirmedOption ? subject : subjectUnconfirmed;
    }

    /**
     * The address from the request is the recipient; {@code app.report.recipient}, when set, gets a
     * copy (once, if it is the same address). Pending the user's confirmation of CC versus override,
     * this is the one place to change it.
     */
    Recipients recipients(BookingTask task) {
        boolean copy = !reportRecipient.isEmpty() && !reportRecipient.equalsIgnoreCase(task.destinationEmail());
        return new Recipients(task.destinationEmail(), copy ? List.of(reportRecipient) : List.of());
    }

    /** The offer to report: the evaluator's pick (confirmed available, tiered price), or none. */
    private static Optional<CallEvidence> chosen(ReportInput in, ReportContext ctx) {
        if (in.bestOffer() == null) {
            return Optional.empty();
        }
        return ctx.calls().stream()
                .filter(c -> c.offer() != null && c.offer().id().equals(in.bestOffer().id()))
                .findFirst();
    }

    /** Never presents an estimate as a stated total. */
    static String priceText(OfferPrice price) {
        if (price == null) {
            return UNKNOWN;
        }
        if (price.estimated()) {
            return "≈ " + price.amount() + " incl. HST; municipal accommodation tax not confirmed "
                    + "(availability confirmed, not booked)";
        }
        return price.amount().amount().toPlainString() + " CAD all-in, availability confirmed, not booked";
    }

    private static String reference(HotelOffer offer) {
        return offer.enquiryId() == null || offer.enquiryId().isBlank() ? null : offer.enquiryId().strip();
    }

    /** One line per hotel called: available + price / sold out / voicemail / failed / ... */
    private String callSummary(CallEvidence c) {
        PhoneCall call = c.call();
        if (c.offer() != null && c.offer().availabilityConfirmed()) {
            return "available, " + evaluator.price(c.offer()).map(ReportComposer::priceText).orElse("no price given");
        }
        if (call.outcome() != null) {
            return switch (call.outcome()) {
                case NO_AVAILABILITY -> "sold out";
                case VOICEMAIL -> "voicemail";
                case TOO_EXPENSIVE -> "available but over budget";
                case IVR_FAILED -> "could not get past the phone menu";
                case HOLD_TIMEOUT -> "on hold too long";
                case WRONG_NUMBER -> "wrong number";
                case REFUSED_AI_CALLER -> "would not talk to an AI caller";
                case CALL_DROPPED -> "call dropped";
                case OFFER_RECEIVED -> "quoted, availability not confirmed";
                case UNKNOWN -> "no clear answer";
            };
        }
        return switch (call.status()) {
            case FAILED, NO_ANSWER, BUSY, CANCELLED -> "failed (" + (call.failureReason() == null
                    ? call.status().toString().toLowerCase().replace('_', ' ') : call.failureReason()) + ")";
            default -> "no result yet";
        };
    }

    private static String stayText(StayDetails stay) {
        return "%s to %s (%d night%s)".formatted(stay.checkIn(), stay.checkOut(), stay.nights(), stay.nights() == 1 ? "" : "s");
    }

    private static String orUnknown(String s) {
        return s == null || s.isBlank() ? UNKNOWN : s;
    }
}
