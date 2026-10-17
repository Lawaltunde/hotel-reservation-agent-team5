package com.reservationagent.hotelreservationagent.support;

import com.reservationagent.hotelreservationagent.domain.geography.GeoPoint;
import com.reservationagent.hotelreservationagent.domain.geography.Municipality;
import com.reservationagent.hotelreservationagent.domain.hotel.Hotel;
import com.reservationagent.hotelreservationagent.domain.hotel.PhoneNumber;
import com.reservationagent.hotelreservationagent.domain.hotel.PriceTier;
import com.reservationagent.hotelreservationagent.domain.hotel.PropertyType;
import com.reservationagent.hotelreservationagent.domain.offer.EvidenceStatus;
import com.reservationagent.hotelreservationagent.domain.offer.HotelOffer;
import com.reservationagent.hotelreservationagent.domain.offer.PriceBreakdown;
import com.reservationagent.hotelreservationagent.domain.offer.TaxInclusion;
import com.reservationagent.hotelreservationagent.domain.shared.Money;
import com.reservationagent.hotelreservationagent.domain.task.AuthorizationMode;
import com.reservationagent.hotelreservationagent.domain.task.BookingTask;
import com.reservationagent.hotelreservationagent.domain.task.StayDetails;
import com.reservationagent.hotelreservationagent.domain.task.TaskIntake;
import com.reservationagent.hotelreservationagent.domain.task.TaskStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Shared test data. Every test that needs a {@link PriceBreakdown} builds it through
 * {@link #breakdown}, so a change to that record's shape is fixed in one place.
 */
public final class Fixtures {

    /** 2026-11-02T12:00:00Z: "now" for every unit test. */
    public static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");
    public static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    public static final LocalDate CHECK_IN = LocalDate.parse("2026-11-20");
    public static final LocalDate CHECK_OUT = LocalDate.parse("2026-11-22");

    private Fixtures() {
    }

    public static Municipality ottawa() {
        return new Municipality(UUID.fromString("00000000-0000-0000-0000-00000000000a"), "Ottawa", null, Set.of(),
                new GeoPoint(45.4215, -75.6972), ZoneId.of("America/Toronto"), null);
    }

    /** An enquiry-only task for 2 adults, 1 room, CHECK_IN to CHECK_OUT, no budget. */
    public static BookingTask task() {
        return task(AuthorizationMode.ENQUIRY_ONLY, null);
    }

    public static BookingTask task(AuthorizationMode mode, Money maximumTotal) {
        return new BookingTask(UUID.randomUUID(), "req-" + UUID.randomUUID(), ottawa().id(),
                new StayDetails(CHECK_IN, CHECK_OUT, 1, 2, 0), "traveller@example.com", mode, maximumTotal,
                Set.of(), false, TaskIntake.FORM, NOW.plusSeconds(7200), TaskStatus.CALLING_FOR_QUOTES, null, NOW, NOW);
    }

    public static Hotel hotel(String name, PropertyType type, Integer stars, PriceTier tier, double phoneConfidence,
                              String... phones) {
        return new Hotel(UUID.randomUUID(), name, "1 Main St, Ottawa, ON", ottawa().id(), new GeoPoint(45.42, -75.69),
                type, stars, null, tier, List.of(phones).stream().map(PhoneNumber::new).toList(), phoneConfidence,
                null, true, NOW);
    }

    /** All mandatory components known, taxes stated separately: comparable total = sum of the parts. */
    public static PriceBreakdown breakdown(String roomSubtotal, String hst, String mat, String mandatoryFees) {
        return new PriceBreakdown(Money.cad(roomSubtotal), Money.cad(hst), Money.cad(mat), Money.cad(mandatoryFees),
                null, null, TaxInclusion.EXCLUDED);
    }

    /** Nothing known: no comparable total. */
    public static PriceBreakdown unknownBreakdown() {
        return new PriceBreakdown(null, null, null, null, null, null, TaxInclusion.UNKNOWN);
    }

    /** An available offer that exactly matches {@link #task()}'s stay, with no expiry. */
    public static HotelOffer offer(BookingTask task, UUID hotelId, PriceBreakdown negotiated, Instant receivedAt) {
        return new HotelOffer(UUID.randomUUID(), task.id(), hotelId, UUID.randomUUID(), true,
                task.stay().checkIn(), task.stay().checkOut(), "Queen room", task.stay().guests(),
                Money.cad(new BigDecimal("400.00")), negotiated, "Free cancellation until 48h before",
                "No deposit", "ENQ-123", null, false, EvidenceStatus.REFERENCE_ID_GIVEN, receivedAt);
    }
}
