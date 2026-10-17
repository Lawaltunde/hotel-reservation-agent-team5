package com.reservationagent.hotelreservationagent.unit.domain;

import com.reservationagent.hotelreservationagent.domain.call.CallOutcome;
import com.reservationagent.hotelreservationagent.domain.call.CallStatus;
import com.reservationagent.hotelreservationagent.domain.call.PhoneCall;
import com.reservationagent.hotelreservationagent.domain.candidate.ExclusionReason;
import com.reservationagent.hotelreservationagent.domain.candidate.HotelCandidate;
import com.reservationagent.hotelreservationagent.domain.hotel.DataSource;
import com.reservationagent.hotelreservationagent.domain.hotel.Hotel;
import com.reservationagent.hotelreservationagent.domain.hotel.HotelSourceRecord;
import com.reservationagent.hotelreservationagent.domain.hotel.PriceTier;
import com.reservationagent.hotelreservationagent.domain.hotel.PropertyType;
import com.reservationagent.hotelreservationagent.domain.offer.EvidenceStatus;
import com.reservationagent.hotelreservationagent.domain.reservation.Reservation;
import com.reservationagent.hotelreservationagent.domain.reservation.ReservationState;
import com.reservationagent.hotelreservationagent.domain.shared.Money;
import com.reservationagent.hotelreservationagent.domain.transcript.Transcript;
import com.reservationagent.hotelreservationagent.domain.transcript.TranscriptTurn;
import com.reservationagent.hotelreservationagent.domain.transcript.TranscriptTurn.Speaker;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.reservationagent.hotelreservationagent.support.Fixtures.NOW;
import static com.reservationagent.hotelreservationagent.support.Fixtures.hotel;
import static org.assertj.core.api.Assertions.assertThat;

class HotelAndRecordsTest {

    @Test
    void hotelPrefersTheFrontDeskOverATollFreeLine() {
        Hotel h = hotel("Chain Inn", PropertyType.HOTEL, 3, PriceTier.MIDSCALE, 0.9, "+18005550100", "+16135550100");
        assertThat(h.bestPhone().e164()).isEqualTo("+16135550100");
    }

    @Test
    void hotelFallsBackToTollFreeWhenItIsTheOnlyNumber() {
        Hotel h = hotel("Chain Inn", PropertyType.HOTEL, 3, PriceTier.MIDSCALE, 0.9, "+18005550100");
        assertThat(h.bestPhone().e164()).isEqualTo("+18005550100");
    }

    @Test
    void hotelWithoutPhonesIsNotCallable() {
        Hotel h = hotel("Quiet B&B", PropertyType.BED_AND_BREAKFAST, null, PriceTier.UNKNOWN, 0);
        assertThat(h.phones()).isEmpty();
        assertThat(h.bestPhone()).isNull();
        assertThat(h.isCallable()).isFalse();
    }

    @Test
    void closedHotelIsNotCallable() {
        Hotel open = hotel("Open", PropertyType.HOTEL, 3, PriceTier.MIDSCALE, 0.9, "+16135550100");
        Hotel closed = new Hotel(open.id(), open.name(), open.address(), open.municipalityId(), open.location(),
                open.propertyType(), open.stars(), open.brand(), open.priceTier(), open.phones(), open.phoneConfidence(),
                open.website(), false, open.lastSeenAt());
        assertThat(open.isCallable()).isTrue();
        assertThat(closed.isCallable()).isFalse();
    }

    @Test
    void hotelPhoneListIsCopiedAndNeverNull() {
        Hotel noPhones = new Hotel(UUID.randomUUID(), "X", null, null, null, PropertyType.HOTEL, null, null,
                PriceTier.UNKNOWN, null, 0, null, true, NOW);
        assertThat(noPhones.phones()).isEmpty();

        var phones = new ArrayList<>(hotel("Y", PropertyType.HOTEL, 3, PriceTier.MIDSCALE, 1, "+16135550100").phones());
        Hotel h = new Hotel(UUID.randomUUID(), "Y", null, null, null, PropertyType.HOTEL, null, null,
                PriceTier.UNKNOWN, phones, 0, null, true, NOW);
        phones.clear();
        assertThat(h.phones()).hasSize(1);
    }

    @Test
    void sourceRecordTagsAreCopied() {
        Map<String, String> tags = new HashMap<>(Map.of("tourism", "hotel"));
        HotelSourceRecord record = new HotelSourceRecord(UUID.randomUUID(), UUID.randomUUID(), DataSource.OPENSTREETMAP,
                "node/1", tags, NOW);
        tags.put("stars", "3");
        assertThat(record.tags()).containsOnlyKeys("tourism");
        assertThat(new HotelSourceRecord(null, null, DataSource.WIKIDATA, "Q1", null, NOW).tags()).isEmpty();
    }

    @Test
    void everyDataSourceNamesItsLicence() {
        assertThat(DataSource.OPENSTREETMAP.licence()).isEqualTo("ODbL-1.0");
        assertThat(DataSource.values()).allSatisfy(s -> assertThat(s.licence()).isNotBlank());
    }

    @Test
    void priceTiersRankCheapestFirstWithUnknownLast() {
        assertThat(PriceTier.values()).extracting(PriceTier::rank).isSorted();
        assertThat(PriceTier.UNKNOWN.rank()).isGreaterThan(PriceTier.LUXURY.rank());
    }

    @Test
    void candidateIsEligibleOnlyWithoutAnExclusionReason() {
        UUID task = UUID.randomUUID();
        assertThat(new HotelCandidate(UUID.randomUUID(), task, UUID.randomUUID(), null, 1.2, 1, null).isEligible()).isTrue();
        assertThat(new HotelCandidate(UUID.randomUUID(), task, UUID.randomUUID(), null, 1.2, null,
                ExclusionReason.NO_PHONE_NUMBER).isEligible()).isFalse();
    }

    @Test
    void callDurationIsZeroUntilBothEndsAreKnown() {
        PhoneCall running = call(NOW, null);
        PhoneCall finished = call(NOW, NOW.plusSeconds(95));
        assertThat(running.duration()).isZero();
        assertThat(call(null, NOW).duration()).isZero();
        assertThat(finished.duration()).isEqualTo(Duration.ofSeconds(95));
    }

    private static PhoneCall call(java.time.Instant start, java.time.Instant end) {
        return new PhoneCall(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, "CA1", "conv-1",
                CallStatus.COMPLETED, CallOutcome.OFFER_RECEIVED, start, end, null);
    }

    @Test
    void reservationIsConfirmedOnlyWithStateAndANumber() {
        assertThat(reservation(ReservationState.CONFIRMED, "HX-42").isConfirmed()).isTrue();
        assertThat(reservation(ReservationState.CONFIRMED, null).isConfirmed()).isFalse();
        assertThat(reservation(ReservationState.CONFIRMED, "  ").isConfirmed()).isFalse();
        assertThat(reservation(ReservationState.UNCERTAIN, "HX-42").isConfirmed()).isFalse();
        assertThat(reservation(ReservationState.ATTEMPT_IN_PROGRESS, null).isConfirmed()).isFalse();
    }

    private static Reservation reservation(ReservationState state, String number) {
        return new Reservation(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), number,
                Money.cad("300"), state, EvidenceStatus.VERBAL_ONLY, NOW);
    }

    @Test
    void transcriptRendersSpeakersInOrder() {
        List<TranscriptTurn> turns = new ArrayList<>(List.of(
                new TranscriptTurn(Speaker.AGENT, "Hi, I'm an AI assistant calling about a rate.", Duration.ZERO),
                new TranscriptTurn(Speaker.HOTEL, "Sure, which dates?", Duration.ofSeconds(4))));
        Transcript transcript = new Transcript(UUID.randomUUID(), UUID.randomUUID(), turns, NOW);
        turns.clear();

        assertThat(transcript.asPlainText())
                .isEqualTo("Agent: Hi, I'm an AI assistant calling about a rate.\nHotel: Sure, which dates?");
        assertThat(new Transcript(null, null, null, NOW).asPlainText()).isEmpty();
    }
}
