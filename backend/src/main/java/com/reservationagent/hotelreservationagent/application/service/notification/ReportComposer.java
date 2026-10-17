package com.reservationagent.hotelreservationagent.application.service.notification;

import com.reservationagent.hotelreservationagent.application.port.out.EmailProvider.Attachment;
import com.reservationagent.hotelreservationagent.application.port.out.EmailProvider.Email;
import com.reservationagent.hotelreservationagent.domain.hotel.Hotel;
import com.reservationagent.hotelreservationagent.domain.offer.HotelOffer;
import com.reservationagent.hotelreservationagent.domain.reservation.Reservation;
import com.reservationagent.hotelreservationagent.domain.shared.Money;
import com.reservationagent.hotelreservationagent.domain.task.BookingTask;
import com.reservationagent.hotelreservationagent.domain.task.TaskOutcome;
import com.reservationagent.hotelreservationagent.domain.transcript.Transcript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Builds the outcome-specific report (README §12). Unknown values are printed as unknown; a
 * reservation number appears only for a confirmed booking.
 */
@Component
public class ReportComposer {

    private static final String UNKNOWN = "Unknown";

    public record ReportInput(BookingTask task, String city, TaskOutcome outcome, HotelOffer bestOffer, Hotel hotel,
                              Reservation reservation, List<Transcript> transcripts, int screened, int called) {
    }

    public Email compose(ReportInput in) {
        BookingTask t = in.task();
        HotelOffer o = in.bestOffer();
        boolean booked = in.reservation() != null && in.reservation().isConfirmed();

        String subject = "Hotel negotiation result — %s, ON — %s to %s".formatted(in.city(), t.stay().checkIn(), t.stay().checkOut());
        String body = """
                Hello,

                Here is the result of your hotel-rate enquiry.

                Status: %s
                Hotel: %s
                Address: %s
                Phone: %s
                Stay: %s to %s | %d room(s) | %d guest(s)
                Room type: %s
                Initial phone quote: %s
                Best negotiated all-in total (CAD): %s
                Savings versus initial phone quote: %s
                Taxes and fees (HST / MAT / other): %s
                Cancellation / payment terms: %s
                Offer expiry: %s
                Hotel enquiry/reference ID: %s
                Reservation confirmation #: %s
                Hotel-issued confirmation: %s
                Hotels screened / called: %d / %d

                The full call transcript(s) are attached.

                Important: an enquiry ID or negotiated quote is not a confirmed booking.
                %s

                Hotel data © OpenStreetMap contributors, Overture Maps Foundation, Foursquare.

                Regards,
                Hotel Deal Agent
                """.formatted(
                in.outcome(),
                in.hotel() == null ? "None selected" : in.hotel().name(),
                in.hotel() == null ? UNKNOWN : orUnknown(in.hotel().address()),
                in.hotel() == null || in.hotel().bestPhone() == null ? UNKNOWN : in.hotel().bestPhone().e164(),
                t.stay().checkIn(), t.stay().checkOut(), t.stay().rooms(), t.stay().guests(),
                o == null ? UNKNOWN : orUnknown(o.roomType()),
                o == null ? UNKNOWN : money(o.initialQuotedTotal()),
                o == null ? UNKNOWN : o.negotiated().comparableTotal().map(Money::toString).orElse(UNKNOWN),
                o == null ? UNKNOWN : money(o.savings()),
                o == null ? UNKNOWN : "HST %s / MAT %s / mandatory fees %s / taxes included: %s".formatted(
                        money(o.negotiated().hst()), money(o.negotiated().municipalAccommodationTax()),
                        money(o.negotiated().mandatoryFees()), o.negotiated().taxInclusion()),
                o == null ? UNKNOWN : orUnknown(o.cancellationTerms()) + " / " + orUnknown(o.depositTerms()),
                o == null || o.quoteValidUntil() == null ? UNKNOWN : o.quoteValidUntil().toString(),
                o == null || o.enquiryId() == null ? "Not provided" : o.enquiryId(),
                booked ? in.reservation().confirmationNumber() : "Not booked",
                o == null ? "Not received" : o.evidenceStatus().toString(),
                in.screened(), in.called(),
                booked ? "" : nextStep(t, in.outcome()));

        String transcriptText = in.transcripts().stream()
                .map(Transcript::asPlainText)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n\n---\n\n"));
        List<Attachment> attachments = transcriptText.isBlank() ? List.of()
                : List.of(new Attachment("transcript.txt", "text/plain", transcriptText.getBytes(StandardCharsets.UTF_8)));
        return new Email(t.destinationEmail(), subject, body, attachments);
    }

    private static String nextStep(BookingTask task, TaskOutcome outcome) {
        return switch (outcome) {
            case ENQUIRY_COMPLETED, QUOTE_RECEIVED -> "To book, contact the hotel directly and quote the reference ID above.";
            case FOLLOW_UP_REQUIRED -> "A booking was authorized but not completed automatically; please contact the hotel.";
            case NO_VALID_QUOTE, NO_ELIGIBLE_HOTELS -> "No comparable offer was obtained. You may widen the dates, budget or area.";
            default -> "";
        };
    }

    private static String money(Money m) {
        return m == null ? UNKNOWN : m.toString();
    }

    private static String orUnknown(String s) {
        return s == null || s.isBlank() ? UNKNOWN : s;
    }
}
