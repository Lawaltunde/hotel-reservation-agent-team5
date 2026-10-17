package com.reservationagent.hotelreservationagent.application.service.notification;

import com.reservationagent.hotelreservationagent.application.port.out.EmailProvider.Attachment;
import com.reservationagent.hotelreservationagent.application.port.out.EmailProvider.Email;
import com.reservationagent.hotelreservationagent.domain.geography.Municipality;
import com.reservationagent.hotelreservationagent.domain.hotel.Hotel;
import com.reservationagent.hotelreservationagent.domain.offer.HotelOffer;
import com.reservationagent.hotelreservationagent.domain.reservation.Reservation;
import com.reservationagent.hotelreservationagent.domain.shared.Money;
import com.reservationagent.hotelreservationagent.domain.task.BookingTask;
import com.reservationagent.hotelreservationagent.domain.task.StayDetails;
import com.reservationagent.hotelreservationagent.domain.task.TaskOutcome;
import com.reservationagent.hotelreservationagent.domain.transcript.Transcript;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Builds the outcome-specific report (README §12). The first block holds the fields the competition
 * requires, in its order; the details below it keep the honest status. Unknown values are printed
 * as unknown; a reservation number appears only for a confirmed booking.
 */
@Component
public class ReportComposer {

    private static final String UNKNOWN = "Unknown";

    private final int teamNumber;
    private final String reportRecipient;

    public ReportComposer(@Value("${app.report.team-number:5}") int teamNumber,
                          @Value("${app.report.recipient:}") String reportRecipient) {
        this.teamNumber = teamNumber;
        this.reportRecipient = reportRecipient == null ? "" : reportRecipient.strip();
    }

    public record ReportInput(BookingTask task, String city, TaskOutcome outcome, HotelOffer bestOffer, Hotel hotel,
                              Reservation reservation, List<Transcript> transcripts, int screened, int called) {
    }

    /** Who gets the report. */
    public record Recipients(String to, List<String> cc) {
    }

    /**
     * @param recordingLine the audio link for the selected hotel's call, or why there is none
     */
    public Email compose(ReportInput in, String recordingLine) {
        BookingTask t = in.task();
        HotelOffer o = in.bestOffer();
        boolean booked = in.reservation() != null && in.reservation().isConfirmed();
        String transcriptText = in.transcripts().stream()
                .map(Transcript::asPlainText)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n\n---\n\n"));

        String body = """
                Hello,

                Here is the result of your hotel-rate enquiry.

                Hotel Name: %s
                Hotel Address: %s
                Hotel Phone: %s
                Reservation #: %s
                Reservation Date: %s
                Price for the stay: %s
                Link To Audio Recording: %s

                Status: %s
                Stay: %d room(s) | %d guest(s)
                Room type: %s
                Initial phone quote: %s
                Best negotiated all-in total (CAD): %s
                Savings versus initial phone quote: %s
                Taxes and fees (HST / MAT / other): %s
                Hotel's stated all-in total: %s
                Cancellation / payment terms: %s
                Offer expiry: %s
                Hotel enquiry/reference ID: %s
                Hotel-issued confirmation: %s
                Hotels screened / called: %d / %d

                %s

                Important: an enquiry ID or negotiated quote is not a confirmed booking.
                %s

                Hotel data © OpenStreetMap contributors, Overture Maps Foundation, Foursquare.

                Regards,
                Hotel Deal Agent
                """.formatted(
                in.hotel() == null ? "None selected" : in.hotel().name(),
                in.hotel() == null ? UNKNOWN : orUnknown(in.hotel().address()),
                in.hotel() == null || in.hotel().bestPhone() == null ? UNKNOWN : in.hotel().bestPhone().e164(),
                booked ? in.reservation().confirmationNumber() : "Not booked (no reservation was made)",
                reservationDate(t.stay(), booked ? in.reservation() : null),
                price(o, booked ? in.reservation() : null),
                recordingLine,
                in.outcome(),
                t.stay().rooms(), t.stay().guests(),
                o == null ? UNKNOWN : orUnknown(o.roomType()),
                o == null ? UNKNOWN : money(o.initialQuotedTotal()),
                o == null ? UNKNOWN : o.negotiated().comparableTotal().map(Money::toString).orElse(UNKNOWN),
                o == null ? UNKNOWN : money(o.savings()),
                o == null ? UNKNOWN : "HST %s / MAT %s / mandatory fees %s / taxes included: %s".formatted(
                        money(o.negotiated().hst()), money(o.negotiated().municipalAccommodationTax()),
                        money(o.negotiated().mandatoryFees()), o.negotiated().taxInclusion()),
                o == null ? UNKNOWN : money(o.negotiated().allInTotal()) + (o.negotiated().totalsDisagree()
                        ? " (does not match the itemised taxes and fees; please confirm with the hotel)" : ""),
                o == null ? UNKNOWN : orUnknown(o.cancellationTerms()) + " / " + orUnknown(o.depositTerms()),
                o == null ? UNKNOWN : expiry(o),
                o == null || o.enquiryId() == null ? "Not provided" : o.enquiryId(),
                o == null ? "Not received" : o.evidenceStatus().toString(),
                in.screened(), in.called(),
                transcriptText.isBlank() ? "No call transcripts." : "The full call transcript(s) are attached.",
                booked ? "" : nextStep(t, in.outcome()));

        List<Attachment> attachments = transcriptText.isBlank() ? List.of()
                : List.of(new Attachment("transcript.txt", "text/plain", transcriptText.getBytes(StandardCharsets.UTF_8)));
        Recipients recipients = recipients(t);
        return new Email(recipients.to(), recipients.cc(), subject(in.outcome(), in.city(), t.stay()), body, attachments);
    }

    /**
     * The competition subject is used only for a confirmed booking: "Reservation Confirmed" on an
     * enquiry would claim a booking that does not exist. Pending the user's decision on that wording,
     * this is the one place to change it.
     */
    String subject(TaskOutcome outcome, String city, StayDetails stay) {
        if (outcome == TaskOutcome.BOOKING_CONFIRMED) {
            return "Team # %d - Hotel Reservation Confirmed!".formatted(teamNumber);
        }
        return "Hotel negotiation result — %s, ON — %s to %s".formatted(city, stay.checkIn(), stay.checkOut());
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

    /** The stay dates; "Booked on" is added only when a booking exists. */
    private static String reservationDate(StayDetails stay, Reservation confirmed) {
        String dates = "%s to %s (%d night%s)".formatted(stay.checkIn(), stay.checkOut(), stay.nights(),
                stay.nights() == 1 ? "" : "s");
        if (confirmed == null) {
            return dates;
        }
        LocalDate bookedOn = confirmed.updatedAt().atZone(Municipality.DEFAULT_TIME_ZONE).toLocalDate();
        return dates + "; Booked on: " + bookedOn;
    }

    private static String price(HotelOffer offer, Reservation confirmed) {
        if (confirmed != null && confirmed.total() != null) {
            return confirmed.total() + " all-in (booked)";
        }
        if (offer == null) {
            return UNKNOWN;
        }
        return offer.negotiated().comparableTotal()
                .map(total -> total + " all-in (HST, MAT and mandatory fees included; quoted, not booked)")
                .orElseGet(() -> offer.negotiated().allInTotal() == null ? UNKNOWN
                        : UNKNOWN + " (the hotel stated " + offer.negotiated().allInTotal() + "; taxes unconfirmed)");
    }

    private static String nextStep(BookingTask task, TaskOutcome outcome) {
        return switch (outcome) {
            case ENQUIRY_COMPLETED, QUOTE_RECEIVED -> "To book, contact the hotel directly and quote the reference ID above.";
            case FOLLOW_UP_REQUIRED -> "A booking was authorized but not completed automatically; please contact the hotel.";
            case NO_VALID_QUOTE, NO_ELIGIBLE_HOTELS -> "No comparable offer was obtained. You may widen the dates, budget or area.";
            default -> "";
        };
    }

    private static String expiry(HotelOffer o) {
        String until = o.quoteValidUntil() == null ? UNKNOWN : o.quoteValidUntil().toString();
        String said = o.quoteValidity().statedText();
        return said == null ? until : "%s (hotel said: \"%s\")".formatted(until, said);
    }

    private static String money(Money m) {
        return m == null ? UNKNOWN : m.toString();
    }

    private static String orUnknown(String s) {
        return s == null || s.isBlank() ? UNKNOWN : s;
    }
}
