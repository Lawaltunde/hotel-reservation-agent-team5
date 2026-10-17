package com.reservationagent.hotelreservationagent.domain.hotel;

/** Coarse price prior used for call ordering when no live price exists. Lower is cheaper. */
public enum PriceTier {
    ECONOMY(1),
    MIDSCALE(2),
    UPSCALE(3),
    LUXURY(4),
    UNKNOWN(5);

    private final int rank;

    PriceTier(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }
}
