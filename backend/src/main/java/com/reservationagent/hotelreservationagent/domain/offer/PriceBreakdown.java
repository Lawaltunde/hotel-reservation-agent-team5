package com.reservationagent.hotelreservationagent.domain.offer;

import com.reservationagent.hotelreservationagent.domain.shared.Money;

import java.util.Optional;

/**
 * Components of a stay price. Null components are unknown, not zero.
 *
 * @param hst                      Ontario HST (13%) amount, if stated
 * @param municipalAccommodationTax MAT amount, if stated
 * @param mandatoryFees            non-removable resort/property/DMF/booking fees
 * @param optionalFees             removable or optional charges; excluded from the comparable total
 */
public record PriceBreakdown(
        Money roomSubtotal,
        Money hst,
        Money municipalAccommodationTax,
        Money mandatoryFees,
        Money optionalFees,
        TaxInclusion taxInclusion
) {

    /**
     * The all-in total used to compare offers, or empty if any mandatory component is unknown.
     * An offer without a comparable total can be reported but never called "cheapest".
     */
    public Optional<Money> comparableTotal() {
        if (roomSubtotal == null || mandatoryFees == null) {
            return Optional.empty();
        }
        if (taxInclusion == TaxInclusion.INCLUDED) {
            return Optional.of(roomSubtotal.plus(mandatoryFees));
        }
        if (taxInclusion == TaxInclusion.EXCLUDED && hst != null && municipalAccommodationTax != null) {
            return Optional.of(roomSubtotal.plus(hst).plus(municipalAccommodationTax).plus(mandatoryFees));
        }
        return Optional.empty();
    }
}
