package com.reservationagent.hotelreservationagent.infrastructure.persistence.candidate;

import com.reservationagent.hotelreservationagent.application.port.out.HotelCandidateRepository;
import com.reservationagent.hotelreservationagent.domain.candidate.HotelCandidate;
import com.reservationagent.hotelreservationagent.domain.candidate.PriceSignal;
import com.reservationagent.hotelreservationagent.domain.shared.Money;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
class HotelCandidatePersistenceAdapter implements HotelCandidateRepository {

    private final HotelCandidateJpaRepository jpa;

    HotelCandidatePersistenceAdapter(HotelCandidateJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public void saveAll(List<HotelCandidate> candidates) {
        jpa.saveAll(candidates.stream().map(HotelCandidatePersistenceAdapter::toEntity).toList());
    }

    @Override
    public List<HotelCandidate> findEligibleInCallOrder(UUID taskId) {
        return jpa.findByTaskIdAndExclusionReasonIsNullOrderByCallPriorityAsc(taskId).stream()
                .map(HotelCandidatePersistenceAdapter::toDomain).toList();
    }

    @Override
    public int countByTask(UUID taskId) {
        return jpa.countByTaskId(taskId);
    }

    private static HotelCandidateEntity toEntity(HotelCandidate c) {
        HotelCandidateEntity e = new HotelCandidateEntity();
        e.id = c.id();
        e.taskId = c.taskId();
        e.hotelId = c.hotelId();
        if (c.priceSignal() != null) {
            e.priceTotal = c.priceSignal().estimatedStayTotal().amount();
            e.priceTaxInclusion = c.priceSignal().taxInclusion();
            e.priceSource = c.priceSignal().source();
            e.priceObservedAt = c.priceSignal().observedAt();
        }
        e.distanceKm = c.distanceKm();
        e.callPriority = c.callPriority();
        e.exclusionReason = c.exclusionReason();
        return e;
    }

    private static HotelCandidate toDomain(HotelCandidateEntity e) {
        PriceSignal price = e.priceTotal == null ? null
                : new PriceSignal(Money.cad(e.priceTotal), e.priceTaxInclusion, e.priceSource, e.priceObservedAt);
        return new HotelCandidate(e.id, e.taskId, e.hotelId, price, e.distanceKm, e.callPriority, e.exclusionReason);
    }
}
