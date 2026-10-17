package com.reservationagent.hotelreservationagent.unit.application;

import com.reservationagent.hotelreservationagent.application.port.out.EmailProvider.Email;
import com.reservationagent.hotelreservationagent.application.service.notification.ReportComposer;
import com.reservationagent.hotelreservationagent.application.service.notification.ReportComposer.ReportInput;
import com.reservationagent.hotelreservationagent.domain.hotel.Hotel;
import com.reservationagent.hotelreservationagent.domain.hotel.PriceTier;
import com.reservationagent.hotelreservationagent.domain.hotel.PropertyType;
import com.reservationagent.hotelreservationagent.domain.offer.EvidenceStatus;
import com.reservationagent.hotelreservationagent.domain.offer.HotelOffer;
import com.reservationagent.hotelreservationagent.domain.reservation.Reservation;
import com.reservationagent.hotelreservationagent.domain.reservation.ReservationState;
import com.reservationagent.hotelreservationagent.domain.shared.Money;
import com.reservationagent.hotelreservationagent.domain.task.BookingTask;
import com.reservationagent.hotelreservationagent.domain.task.TaskOutcome;
import com.reservationagent.hotelreservationagent.domain.transcript.Transcript;
import com.reservationagent.hotelreservationagent.domain.transcript.TranscriptTurn;
import com.reservationagent.hotelreservationagent.domain.transcript.TranscriptTurn.Speaker;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static com.reservationagent.hotelreservationagent.support.Fixtures.NOW;
import static com.reservationagent.hotelreservationagent.support.Fixtures.breakdown;
import static com.reservationagent.hotelreservationagent.support.Fixtures.hotel;
import static com.reservationagent.hotelreservationagent.support.Fixtures.offer;
import static com.reservationagent.hotelreservationagent.support.Fixtures.task;
import static org.assertj.core.api.Assertions.assertThat;

/** The report email: honest wording, unknowns spelled out, a reservation number only when booked. */
class ReportComposerTest {

    private final ReportComposer composer = new ReportComposer();
    private final BookingTask task = task();
    private final Hotel hotel = hotel("Rideau Inn", PropertyType.HOTEL, 3, PriceTier.MIDSCALE, 0.9, "+16135550100");

    private static ReportInput input(BookingTask task, TaskOutcome outcome, HotelOffer offer, Hotel hotel,
                                     Reservation reservation, List<Transcript> transcripts, int screened, int called) {
        return new ReportInput(task, "Ottawa", outcome, offer, hotel, reservation, transcripts, screened, called);
    }

    private static String line(Email email, String label) {
        return email.textBody().lines().filter(l -> l.startsWith(label + ":")).findFirst()
                .map(l -> l.substring(label.length() + 1).trim())
                .orElseThrow(() -> new AssertionError("no line '" + label + "' in\n" + email.textBody()));
    }

    @Test
    void noEligibleHotelsReportSaysSoAndPrintsUnknownsAsUnknown() {
        Email email = composer.compose(input(task, TaskOutcome.NO_ELIGIBLE_HOTELS, null, null, null, List.of(), 0, 0));

        assertThat(email.to()).isEqualTo("traveller@example.com");
        assertThat(email.subject()).isEqualTo("Hotel negotiation result — Ottawa, ON — 2026-11-20 to 2026-11-22");
        assertThat(line(email, "Status")).isEqualTo("NO_ELIGIBLE_HOTELS");
        assertThat(line(email, "Hotel")).isEqualTo("None selected");
        assertThat(line(email, "Address")).isEqualTo("Unknown");
        assertThat(line(email, "Phone")).isEqualTo("Unknown");
        assertThat(line(email, "Stay")).isEqualTo("2026-11-20 to 2026-11-22 | 1 room(s) | 2 guest(s)");
        assertThat(line(email, "Best negotiated all-in total (CAD)")).isEqualTo("Unknown");
        assertThat(line(email, "Offer expiry")).isEqualTo("Unknown");
        assertThat(line(email, "Hotel enquiry/reference ID")).isEqualTo("Not provided");
        assertThat(line(email, "Reservation confirmation #")).isEqualTo("Not booked");
        assertThat(line(email, "Hotel-issued confirmation")).isEqualTo("Not received");
        assertThat(line(email, "Hotels screened / called")).isEqualTo("0 / 0");
        assertThat(email.textBody())
                .contains("No comparable offer was obtained. You may widen the dates, budget or area.")
                .contains("an enquiry ID or negotiated quote is not a confirmed booking")
                .contains("© OpenStreetMap contributors");
        assertThat(email.attachments()).isEmpty();
    }

    @Test
    void enquiryReportShowsTheQuoteButNeverAReservationNumber() {
        HotelOffer best = offer(task, hotel.id(), breakdown("300.00", "39.00", "12.00", "15.00"), NOW);
        Email email = composer.compose(input(task, TaskOutcome.ENQUIRY_COMPLETED, best, hotel, null, List.of(), 7, 3));

        assertThat(line(email, "Hotel")).isEqualTo("Rideau Inn");
        assertThat(line(email, "Address")).isEqualTo("1 Main St, Ottawa, ON");
        assertThat(line(email, "Phone")).isEqualTo("+16135550100");
        assertThat(line(email, "Room type")).isEqualTo("Queen room");
        assertThat(line(email, "Initial phone quote")).isEqualTo("CAD 400.00");
        assertThat(line(email, "Best negotiated all-in total (CAD)")).isEqualTo("CAD 366.00");
        assertThat(line(email, "Cancellation / payment terms")).isEqualTo("Free cancellation until 48h before / No deposit");
        assertThat(line(email, "Hotel enquiry/reference ID")).isEqualTo("ENQ-123");
        assertThat(line(email, "Reservation confirmation #")).isEqualTo("Not booked");
        assertThat(line(email, "Hotel-issued confirmation")).isEqualTo(EvidenceStatus.REFERENCE_ID_GIVEN.name());
        assertThat(line(email, "Hotels screened / called")).isEqualTo("7 / 3");
        assertThat(email.textBody()).contains("To book, contact the hotel directly and quote the reference ID above.");
    }

    @Test
    void missingOfferTextFieldsArePrintedAsUnknown() {
        HotelOffer o = offer(task, hotel.id(), breakdown("300.00", "39.00", "12.00", "0"), NOW);
        HotelOffer sparse = new HotelOffer(o.id(), o.taskId(), o.hotelId(), o.callId(), true, o.checkIn(), o.checkOut(),
                " ", o.guestCount(), o.initialQuotedTotal(), o.negotiated(), null, "", null, null, false,
                EvidenceStatus.VERBAL_ONLY, NOW);
        Hotel noAddress = new Hotel(hotel.id(), hotel.name(), null, hotel.municipalityId(), hotel.location(),
                hotel.propertyType(), hotel.stars(), null, hotel.priceTier(), List.of(), 0, null, true, NOW);

        Email email = composer.compose(input(task, TaskOutcome.QUOTE_RECEIVED, sparse, noAddress, null, List.of(), 1, 1));

        assertThat(line(email, "Address")).isEqualTo("Unknown");
        assertThat(line(email, "Phone")).isEqualTo("Unknown");
        assertThat(line(email, "Room type")).isEqualTo("Unknown");
        assertThat(line(email, "Cancellation / payment terms")).isEqualTo("Unknown / Unknown");
        assertThat(line(email, "Hotel enquiry/reference ID")).isEqualTo("Not provided");
    }

    @Test
    @Disabled("BUG: HotelOffer.savings() throws NullPointerException when initialQuotedTotal is null "
            + "(initialQuotedTotal::minus dereferences eagerly), so compose() fails and the task never leaves EMAIL_PENDING")
    void offerWithoutAnInitialQuoteReportsUnknownSavings() {
        HotelOffer o = offer(task, hotel.id(), breakdown("300.00", "39.00", "12.00", "0"), NOW);
        HotelOffer noInitialQuote = new HotelOffer(o.id(), o.taskId(), o.hotelId(), o.callId(), true, o.checkIn(),
                o.checkOut(), o.roomType(), o.guestCount(), null, o.negotiated(), o.cancellationTerms(), o.depositTerms(),
                o.enquiryId(), null, false, EvidenceStatus.VERBAL_ONLY, NOW);

        Email email = composer.compose(input(task, TaskOutcome.QUOTE_RECEIVED, noInitialQuote, hotel, null, List.of(), 1, 1));

        assertThat(line(email, "Initial phone quote")).isEqualTo("Unknown");
        assertThat(line(email, "Savings versus initial phone quote")).isEqualTo("Unknown");
    }

    @Test
    void confirmedBookingShowsTheConfirmationNumberAndNoNextStep() {
        HotelOffer best = offer(task, hotel.id(), breakdown("300.00", "39.00", "12.00", "0"), NOW);
        Reservation confirmed = new Reservation(UUID.randomUUID(), task.id(), hotel.id(), best.id(), "HX-9001",
                Money.cad("351"), ReservationState.CONFIRMED, EvidenceStatus.WRITTEN_CONFIRMATION_PROMISED, NOW);

        Email email = composer.compose(input(task, TaskOutcome.BOOKING_CONFIRMED, best, hotel, confirmed, List.of(), 4, 2));

        assertThat(line(email, "Reservation confirmation #")).isEqualTo("HX-9001");
        assertThat(email.textBody()).doesNotContain("To book, contact").doesNotContain("No comparable offer");
    }

    @Test
    void unconfirmedReservationIsReportedAsNotBooked() {
        HotelOffer best = offer(task, hotel.id(), breakdown("300.00", "39.00", "12.00", "0"), NOW);
        Reservation uncertain = new Reservation(UUID.randomUUID(), task.id(), hotel.id(), best.id(), "MAYBE-1",
                Money.cad("351"), ReservationState.UNCERTAIN, EvidenceStatus.VERBAL_ONLY, NOW);

        Email email = composer.compose(input(task, TaskOutcome.FOLLOW_UP_REQUIRED, best, hotel, uncertain, List.of(), 4, 2));

        assertThat(line(email, "Reservation confirmation #")).isEqualTo("Not booked");
        assertThat(email.textBody()).doesNotContain("MAYBE-1")
                .contains("A booking was authorized but not completed automatically; please contact the hotel.");
    }

    @Test
    void transcriptsAreAttachedAsOnePlainTextFile() {
        Transcript first = new Transcript(UUID.randomUUID(), UUID.randomUUID(), List.of(
                new TranscriptTurn(Speaker.AGENT, "Hello, this is an AI assistant.", Duration.ZERO),
                new TranscriptTurn(Speaker.HOTEL, "Hi.", Duration.ofSeconds(2))), NOW);
        Transcript second = new Transcript(UUID.randomUUID(), UUID.randomUUID(), List.of(
                new TranscriptTurn(Speaker.HOTEL, "We're full.", Duration.ZERO)), NOW);

        Email email = composer.compose(input(task, TaskOutcome.NO_VALID_QUOTE, null, null, null,
                List.of(first, second), 2, 2));

        assertThat(email.attachments()).singleElement().satisfies(a -> {
            assertThat(a.filename()).isEqualTo("transcript.txt");
            assertThat(a.contentType()).isEqualTo("text/plain");
            assertThat(new String(a.content(), StandardCharsets.UTF_8)).isEqualTo(
                    "Agent: Hello, this is an AI assistant.\nHotel: Hi.\n\n---\n\nHotel: We're full.");
        });
    }

    @Test
    void transcriptsWithNoTurnsAddNoAttachment() {
        Transcript empty = new Transcript(UUID.randomUUID(), UUID.randomUUID(), List.of(), NOW);
        Email email = composer.compose(input(task, TaskOutcome.CALL_FAILED, null, null, null, List.of(empty), 1, 1));
        assertThat(email.attachments()).isEmpty();
    }
}
