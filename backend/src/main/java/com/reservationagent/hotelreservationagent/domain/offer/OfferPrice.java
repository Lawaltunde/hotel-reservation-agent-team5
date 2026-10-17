package com.reservationagent.hotelreservationagent.domain.offer;

import com.reservationagent.hotelreservationagent.domain.shared.Money;

/**
 * The price used to rank and report an offer, and how firm it is.
 *
 * @param amount the stay total in CAD
 * @param basis  where the amount comes from; only {@link PriceBasis#ESTIMATED_HST} is an estimate
 */
public record OfferPrice(Money amount, PriceBasis basis) {

    public boolean estimated() {
        return basis.estimated();
    }

    public enum PriceBasis {
        /** Tier A: room subtotal + HST + MAT + mandatory fees, all stated. */
        SUM_OF_STATED_PARTS(false),
        /** Tier A: the hotel's stated all-in total, taxes included. */
        STATED_ALL_IN(false),
        /**
         * Tier B: a stated pre-tax charge plus Ontario HST at the legal 13% (or the stated HST) and
         * any stated mandatory fees. The municipal accommodation tax is not confirmed and not added.
         */
        ESTIMATED_HST(true);

        private final boolean estimated;

        PriceBasis(boolean estimated) {
            this.estimated = estimated;
        }

        public boolean estimated() {
            return estimated;
        }
    }
}
