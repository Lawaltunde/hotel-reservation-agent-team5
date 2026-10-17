package com.reservationagent.hotelreservationagent.domain.task;

import com.reservationagent.hotelreservationagent.domain.shared.DomainException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Requested stay. Dates are in the hotel's local time zone. */
public record StayDetails(LocalDate checkIn, LocalDate checkOut, int rooms, int adults, int children) {

    public StayDetails {
        if (checkIn == null || checkOut == null || !checkOut.isAfter(checkIn)) {
            throw new DomainException("checkOut must be after checkIn");
        }
        if (rooms < 1 || adults < 1 || children < 0) {
            throw new DomainException("rooms and adults must be positive, children non-negative");
        }
    }

    public long nights() {
        return ChronoUnit.DAYS.between(checkIn, checkOut);
    }

    public int guests() {
        return adults + children;
    }
}
