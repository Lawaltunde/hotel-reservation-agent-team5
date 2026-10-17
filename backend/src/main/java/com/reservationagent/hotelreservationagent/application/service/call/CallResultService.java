package com.reservationagent.hotelreservationagent.application.service.call;

import com.reservationagent.hotelreservationagent.application.port.out.AiUsageRepository;
import com.reservationagent.hotelreservationagent.application.port.out.BookingTaskRepository;
import com.reservationagent.hotelreservationagent.application.port.out.OfferExtractor;
import com.reservationagent.hotelreservationagent.application.port.out.OfferExtractor.ExtractedOffer;
import com.reservationagent.hotelreservationagent.application.port.out.OfferRepository;
import com.reservationagent.hotelreservationagent.application.port.out.PhoneCallRepository;
import com.reservationagent.hotelreservationagent.application.port.out.TranscriptRepository;
import com.reservationagent.hotelreservationagent.application.port.out.WebhookPayloadParser.CallCompleted;
import com.reservationagent.hotelreservationagent.domain.call.CallOutcome;
import com.reservationagent.hotelreservationagent.domain.call.CallStatus;
import com.reservationagent.hotelreservationagent.domain.call.PhoneCall;
import com.reservationagent.hotelreservationagent.domain.offer.EvidenceStatus;
import com.reservationagent.hotelreservationagent.domain.offer.HotelOffer;
import com.reservationagent.hotelreservationagent.domain.offer.PriceBreakdown;
import com.reservationagent.hotelreservationagent.domain.offer.QuoteValidity;
import com.reservationagent.hotelreservationagent.domain.offer.TaxInclusion;
import com.reservationagent.hotelreservationagent.domain.shared.Money;
import com.reservationagent.hotelreservationagent.domain.task.BookingTask;
import com.reservationagent.hotelreservationagent.domain.transcript.Transcript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Persists a finished call: transcript, extracted outcome, validated offer. */
@Service
public class CallResultService {

    private static final Duration TRANSCRIPT_RETENTION = Duration.ofDays(30);

    private final PhoneCallRepository calls;
    private final TranscriptRepository transcripts;
    private final OfferRepository offers;
    private final OfferExtractor extractor;
    private final BookingTaskRepository tasks;
    private final Clock clock;
    private final AiUsageRepository usage;

    public CallResultService(PhoneCallRepository calls, TranscriptRepository transcripts, OfferRepository offers,
                             OfferExtractor extractor, BookingTaskRepository tasks, Clock clock, AiUsageRepository usage) {
        this.usage = usage;
        this.calls = calls;
        this.transcripts = transcripts;
        this.offers = offers;
        this.extractor = extractor;
        this.tasks = tasks;
        this.clock = clock;
    }

    /** @return the task ID the call belonged to, if known */
    @Transactional
    public Optional<UUID> record(CallCompleted completed) {
        Optional<PhoneCall> maybeCall = calls.findByConversationId(completed.conversationId());
        if (maybeCall.isEmpty()) {
            return Optional.empty();
        }
        PhoneCall call = maybeCall.get();
        BookingTask task = tasks.findById(call.taskId()).orElseThrow();
        Transcript transcript = transcripts.save(new Transcript(UUID.randomUUID(), call.id(), completed.turns(),
                clock.instant().plus(TRANSCRIPT_RETENTION)));

        Instant endedAt = completed.endedAt() == null ? clock.instant() : completed.endedAt();
        if (completed.durationSeconds() > 0) {
            usage.record(task.id(), "elevenlabs-call", AiUsageRepository.TokenUsage.NONE, completed.durationSeconds());
        }
        CallOutcome outcome = CallOutcome.CALL_DROPPED;
        if (completed.callSucceeded() && !transcript.turns().isEmpty()) {
            OfferExtractor.Extraction extraction = extractor.extract(transcript.asPlainText(), task.stay());
            usage.record(task.id(), "offer-extraction", extraction.usage(), 0);
            ExtractedOffer extracted = extraction.offer();
            outcome = extracted.callOutcome() == null ? CallOutcome.UNKNOWN : extracted.callOutcome();
            if (outcome == CallOutcome.OFFER_RECEIVED && "CAD".equalsIgnoreCase(extracted.currency())) {
                offers.save(toOffer(task, call, extracted, endedAt));
            }
        }
        calls.save(new PhoneCall(call.id(), call.taskId(), call.hotelId(), call.dialledNumber(), call.providerCallId(),
                call.conversationId(), completed.callSucceeded() ? CallStatus.COMPLETED : CallStatus.FAILED, outcome,
                call.startedAt(), endedAt, completed.failureReason()));
        return Optional.of(task.id());
    }

    private HotelOffer toOffer(BookingTask task, PhoneCall call, ExtractedOffer e, Instant callEndedAt) {
        PriceBreakdown breakdown = new PriceBreakdown(money(e.roomSubtotal()), money(e.hst()),
                money(e.municipalAccommodationTax()), money(e.mandatoryFees()), money(e.optionalFees()),
                money(e.allInTotal()), taxInclusion(e.taxesIncludedInQuote()));
        EvidenceStatus evidence = Boolean.TRUE.equals(e.writtenConfirmationPromised())
                ? EvidenceStatus.WRITTEN_CONFIRMATION_PROMISED
                : e.enquiryId() != null ? EvidenceStatus.REFERENCE_ID_GIVEN : EvidenceStatus.VERBAL_ONLY;
        return new HotelOffer(UUID.randomUUID(), task.id(), call.hotelId(), call.id(),
                Boolean.TRUE.equals(e.availabilityConfirmed()),
                // The agent asked about the task's stay; a hotel that answers without restating the
                // dates or occupancy ("yes, tonight we have...") is answering about that stay.
                e.checkIn() == null ? task.stay().checkIn() : e.checkIn(),
                e.checkOut() == null ? task.stay().checkOut() : e.checkOut(),
                e.roomType(),
                e.guestCount() == null ? task.stay().guests() : e.guestCount(), money(e.initialQuotedTotal()), breakdown,
                e.cancellationTerms(), e.depositTerms(), e.enquiryId(),
                QuoteValidity.stated(e.quoteValidityText(), e.quoteValidityHours(), callEndedAt),
                Boolean.TRUE.equals(e.roomHeld()), evidence, clock.instant());
    }

    private static Money money(BigDecimal amount) {
        return amount == null ? null : Money.cad(amount);
    }

    private static TaxInclusion taxInclusion(String value) {
        if (value == null) return TaxInclusion.UNKNOWN;
        try {
            return TaxInclusion.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return TaxInclusion.UNKNOWN;
        }
    }
}
