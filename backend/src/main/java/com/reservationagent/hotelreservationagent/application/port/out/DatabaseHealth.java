package com.reservationagent.hotelreservationagent.application.port.out;

/** Whether the database answers, for the API health check. */
public interface DatabaseHealth {

    /** @return true if a trivial query succeeds within a short time */
    boolean isUp();
}
