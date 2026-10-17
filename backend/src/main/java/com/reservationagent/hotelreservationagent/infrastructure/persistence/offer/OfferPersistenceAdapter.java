package com.reservationagent.hotelreservationagent.infrastructure.persistence.offer;

import com.reservationagent.hotelreservationagent.application.port.out.OfferRepository;
import com.reservationagent.hotelreservationagent.domain.offer.HotelOffer;
import com.reservationagent.hotelreservationagent.domain.offer.PriceBreakdown;
import com.reservationagent.hotelreservationagent.domain.shared.Money;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
class OfferPersistenceAdapter implements OfferRepository {

    private final HotelOfferJpaRepository jpa;

    OfferPersistenceAdapter(HotelOfferJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public HotelOffer save(HotelOffer o) {
        HotelOfferEntity e = new HotelOfferEntity();
        e.id = o.id();
        e.taskId = o.taskId();
        e.hotelId = o.hotelId();
        e.callId = o.callId();
        e.availabilityConfirmed = o.availabilityConfirmed();
        e.checkIn = o.checkIn();
        e.checkOut = o.checkOut();
        e.roomType = o.roomType();
        e.guestCount = o.guestCount();
        e.initialQuotedTotal = amount(o.initialQuotedTotal());
        PriceBreakdown p = o.negotiated();
        e.roomSubtotal = amount(p.roomSubtotal());
        e.hst = amount(p.hst());
        e.municipalAccommodationTax = amount(p.municipalAccommodationTax());
        e.mandatoryFees = amount(p.mandatoryFees());
        e.optionalFees = amount(p.optionalFees());
        e.taxInclusion = p.taxInclusion();
        e.comparableTotal = p.comparableTotal().map(Money::amount).orElse(null);
        e.cancellationTerms = o.cancellationTerms();
        e.depositTerms = o.depositTerms();
        e.enquiryId = o.enquiryId();
        e.quoteValidUntil = o.quoteValidUntil();
        e.holdsRoom = o.holdsRoom();
        e.evidenceStatus = o.evidenceStatus();
        e.receivedAt = o.receivedAt();
        jpa.save(e);
        return o;
    }

    @Override
    public List<HotelOffer> findByTask(UUID taskId) {
        return jpa.findByTaskIdOrderByReceivedAtAsc(taskId).stream().map(OfferPersistenceAdapter::toDomain).toList();
    }

    private static HotelOffer toDomain(HotelOfferEntity e) {
        PriceBreakdown p = new PriceBreakdown(money(e.roomSubtotal), money(e.hst), money(e.municipalAccommodationTax),
                money(e.mandatoryFees), money(e.optionalFees), e.taxInclusion);
        return new HotelOffer(e.id, e.taskId, e.hotelId, e.callId, e.availabilityConfirmed, e.checkIn, e.checkOut,
                e.roomType, e.guestCount, money(e.initialQuotedTotal), p, e.cancellationTerms, e.depositTerms,
                e.enquiryId, e.quoteValidUntil, e.holdsRoom, e.evidenceStatus, e.receivedAt);
    }

    private static BigDecimal amount(Money m) {
        return m == null ? null : m.amount();
    }

    private static Money money(BigDecimal b) {
        return b == null ? null : Money.cad(b);
    }
}
