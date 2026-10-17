package com.reservationagent.hotelreservationagent.domain.offer;

/** Whether a quoted amount already contains a tax. UNKNOWN is never assumed to be INCLUDED. */
public enum TaxInclusion {
    INCLUDED,
    EXCLUDED,
    UNKNOWN
}
