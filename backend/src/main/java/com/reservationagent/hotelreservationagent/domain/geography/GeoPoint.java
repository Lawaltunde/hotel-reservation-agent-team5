package com.reservationagent.hotelreservationagent.domain.geography;

import com.reservationagent.hotelreservationagent.domain.shared.DomainException;

/** WGS84 coordinate. */
public record GeoPoint(double latitude, double longitude) {

    public GeoPoint {
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new DomainException("Invalid coordinate: " + latitude + "," + longitude);
        }
    }
}
