package com.reservationagent.hotelreservationagent.application.service.notification;

import com.reservationagent.hotelreservationagent.application.port.out.AiUsageRepository;
import com.reservationagent.hotelreservationagent.application.port.out.EmailProvider.Email;
import com.reservationagent.hotelreservationagent.application.service.recording.CallRecordingService.IssuedLink;
import com.reservationagent.hotelreservationagent.domain.call.PhoneCall;
import com.reservationagent.hotelreservationagent.domain.hotel.Hotel;
import com.reservationagent.hotelreservationagent.domain.offer.HotelOffer;
import com.reservationagent.hotelreservationagent.domain.offer.PriceBreakdown;
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
    public static final String CURRENCY = Money.CURRENCY;

    private final String subject;
    private final String reportRecipient;
    private final String from;
    private final Clock clock;

    public ReportComposer(@Value("${app.report.subject:Team 5 - Hotel Reservation Enquiry Result}") String subject,
                          @Value("${app.report.recipient:}") String reportRecipient,
                          @Value("${hotel-agent.resend.from:}") String from,
                          Clock clock) {
        this.subject = subject;
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

    /** A price for the stay and what it includes. */
    record Price(Money amount, String note) {
    }

    public Composed compose(ReportInput in, ReportContext ctx) {
        BookingTask t = in.task();
        Optional<CallEvidence> chosen = chosen(in, ctx);
        HotelOffer offer = chosen.map(CallEvidence::offer).orElse(null);
        Hotel hotel = chosen.map(CallEvidence::hotel).orElse(null);
        Price price = offer == null ? null : price(offer.negotiated()).orElse(null);
        StayDetails stay = t.stay();

        StringBuilder b = new StringBuilder();
        b.append(subject).append("\n\n");
        if (chosen.isPresent()) {
            b.append("Cheapest confirmed available option among the hotels called:\n\n");
            b.append("Hotel Name: ").append(hotel == null ? UNKNOWN : hotel.name()).append('\n');
            b.append("Hotel Address: ").append(hotel == null ? UNKNOWN : orUnknown(hotel.address())).append('\n');
            b.append("Hotel Phone (number called): ").append(chosen.get().call().dialledNumber().e164()).append('\n');
            b.append("Stay: ").append(stayText(stay)).append('\n');
            b.append("Total price for the stay: ")
                    .append(price == null ? UNKNOWN : price.amount() + " (" + price.note() + ")").append('\n');
            if (offer.enquiryId() != null && !offer.enquiryId().isBlank()) {
                b.append("Reference #: ").append(offer.enquiryId()).append('\n');
            }
            b.append("Availability: confirmed by the hotel on the call\n");
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
        Email email = new Email(recipients.to(), recipients.cc(), subject(), body, List.of());
        SentReport.Fields fields = new SentReport.Fields(
                hotel == null ? null : hotel.name(),
                hotel == null ? null : hotel.address(),
                chosen.map(c -> c.call().dialledNumber().e164()).orElse(null),
                offer == null || offer.enquiryId() == null || offer.enquiryId().isBlank() ? null : offer.enquiryId(),
                stay.checkIn(), stay.checkOut(),
                price == null ? null : price.amount().amount(), CURRENCY,
                price == null ? null : price.note(),
                chosen.map(c -> c.recording().url()).orElse(null),
                chosen.map(CallEvidence::conversationUrl).orElse(null));
        SentReport report = new SentReport(subject(), from, recipients.to(), recipients.cc(), body, null, fields,
                new SentReport.Tokens(usage.promptTokens(), usage.completionTokens(), usage.callSeconds()),
                transcripts, clock.instant());
        return new Composed(email, report);
    }

    /** A minimal report that relies on as little as possible; used when {@link #compose} fails. */
    public Composed fallback(ReportInput in) {
        BookingTask t = in.task();
        StringBuilder body = new StringBuilder(subject()).append("\n\n");
        body.append("Status: ").append(in.outcome()).append('\n');
        body.append("Stay: ").append(t.stay().checkIn()).append(" to ").append(t.stay().checkOut()).append('\n');
        if (in.hotel() != null) {
            body.append("Hotel Name: ").append(in.hotel().name()).append('\n');
            body.append("Hotel Address: ").append(orUnknown(in.hotel().address())).append('\n');
            body.append("Hotel Phone: ").append(in.hotel().bestPhone() == null ? UNKNOWN : in.hotel().bestPhone().e164()).append('\n');
        }
        if (in.bestOffer() != null) {
            body.append("Total price for the stay: ").append(in.bestOffer().negotiated().comparableTotal()
                    .map(Money::toString).orElse(UNKNOWN)).append('\n');
        }
        body.append("\nSome report details could not be produced. No booking, reservation, hold or payment was made.\n");
        Recipients recipients = recipients(t);
        Email email = new Email(recipients.to(), recipients.cc(), subject(), body.toString(), List.of());
        SentReport report = new SentReport(subject(), from, recipients.to(), recipients.cc(), body.toString(), null,
                null, null, List.of(), clock.instant());
        return new Composed(email, report);
    }

    /** The subject comes from {@code app.report.subject}; this is the one place that decides it. */
    String subject() {
        return subject;
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

    /**
     * The offer to report: the evaluator's pick (comparable all-in total, within budget) if there is
     * one, otherwise the cheapest offer whose availability the hotel confirmed for the asked dates
     * and that has any known price, labelled with what the price leaves out.
     */
    private static Optional<CallEvidence> chosen(ReportInput in, ReportContext ctx) {
        if (in.bestOffer() != null) {
            Optional<CallEvidence> best = ctx.calls().stream()
                    .filter(c -> c.offer() != null && c.offer().id().equals(in.bestOffer().id()))
                    .findFirst();
            if (best.isPresent()) return best;
        }
        StayDetails stay = in.task().stay();
        return ctx.calls().stream()
                .filter(c -> c.offer() != null && c.offer().availabilityConfirmed())
                .filter(c -> stay.checkIn().equals(c.offer().checkIn()) && stay.checkOut().equals(c.offer().checkOut()))
                .filter(c -> price(c.offer().negotiated()).isPresent())
                .min(Comparator.comparing((CallEvidence c) -> price(c.offer().negotiated()).orElseThrow().amount())
                        .thenComparing(c -> c.offer().receivedAt()));
    }

    /** The best-known stay total: comparable all-in, else the hotel's stated total, else the room charge. */
    static Optional<Price> price(PriceBreakdown p) {
        Optional<Money> comparable = p.comparableTotal();
        if (comparable.isPresent()) {
            return Optional.of(new Price(comparable.get(), "all-in: taxes and mandatory fees included; quoted, not booked"
                    + (p.totalsDisagree() ? "; the hotel's stated total differs, please confirm" : "")));
        }
        if (p.allInTotal() != null) {
            return Optional.of(new Price(p.allInTotal(), "total as stated by the hotel; whether all taxes are included was not confirmed"));
        }
        if (p.roomSubtotal() != null) {
            return Optional.of(new Price(p.roomSubtotal(), "room charge before taxes and fees; taxes not confirmed"));
        }
        return Optional.empty();
    }

    private static String callSummary(CallEvidence c) {
        if (c.offer() != null) {
            String priceText = price(c.offer().negotiated()).map(p -> p.amount().toString()).orElse("no price given");
            return (c.offer().availabilityConfirmed() ? "available, " : "availability not confirmed, ") + priceText;
        }
        return c.call().outcome() == null ? String.valueOf(c.call().status()) : c.call().outcome().toString();
    }

    private static String stayText(StayDetails stay) {
        return "%s to %s (%d night%s)".formatted(stay.checkIn(), stay.checkOut(), stay.nights(), stay.nights() == 1 ? "" : "s");
    }

    private static String orUnknown(String s) {
        return s == null || s.isBlank() ? UNKNOWN : s;
    }
}
