package com.reservationagent.hotelreservationagent.domain.offer;

import com.reservationagent.hotelreservationagent.domain.shared.Money;
import java.math.BigDecimal;
import java.util.Optional;

/**
 * Components of a stay price, as stated by the hotel. Null components are unknown, not zero.
 * All arithmetic happens here, in Java; the language model only copies stated amounts.
 *
 * @param roomSubtotal              room charge for the whole stay BEFORE taxes and fees
 * @param hst                       Ontario HST (13%) amount, if stated
 * @param municipalAccommodationTax MAT amount, if stated (0 only if the hotel said there is none)
 * @param mandatoryFees             non-removable resort/property/DMF/booking fees (0 only if the hotel said none)
 * @param optionalFees              removable or optional charges; never part of the comparable total
 * @param allInTotal                the final total the hotel stated, including taxes and mandatory fees
 * @param taxInclusion              whether {@code allInTotal} includes HST and MAT
 */
public record PriceBreakdown(
        Money roomSubtotal,
        Money hst,
        Money municipalAccommodationTax,
        Money mandatoryFees,
        Money optionalFees,
        Money allInTotal,
        TaxInclusion taxInclusion
) {

    /** Stated and computed totals may differ by rounding; more than this is flagged. */
    public static final Money DISAGREEMENT_TOLERANCE = Money.cad(BigDecimal.ONE);

    public PriceBreakdown {
        taxInclusion = taxInclusion == null ? TaxInclusion.UNKNOWN : taxInclusion;
    }

    /**
     * The all-in total used to compare offers, or empty if it cannot be established:
     * <ol>
     *   <li>if the room subtotal, HST, MAT and mandatory fees are all known, their sum;</li>
     *   <li>otherwise, if the hotel stated an all-in total that includes taxes, that total;</li>
     *   <li>otherwise empty. An offer without a comparable total can be reported but never called cheapest.</li>
     * </ol>
     * When both (1) and a stated all-in total exist, (1) wins; see {@link #totalsDisagree()}.
     */
    public Optional<Money> comparableTotal() {
        Optional<Money> computed = computedTotal();
        if (computed.isPresent()) {
            return computed;
        }
        if (allInTotal != null && taxInclusion == TaxInclusion.INCLUDED) {
            return Optional.of(allInTotal);
        }
        return Optional.empty();
    }

    /** Rule (1): subtotal + HST + MAT + mandatory fees, when every component is known. */
    public Optional<Money> computedTotal() {
        if (roomSubtotal == null || hst == null || municipalAccommodationTax == null || mandatoryFees == null) {
            return Optional.empty();
        }
        return Optional.of(roomSubtotal.plus(hst).plus(municipalAccommodationTax).plus(mandatoryFees));
    }

    /**
     * True when the components add up to something more than {@link #DISAGREEMENT_TOLERANCE} away
     * from the all-in total the hotel stated. The computed sum is still used; the report flags it.
     */
    public boolean totalsDisagree() {
        return allInTotal != null && computedTotal()
                .map(sum -> sum.minus(allInTotal).abs().compareTo(DISAGREEMENT_TOLERANCE) > 0)
                .orElse(false);
    }
}
