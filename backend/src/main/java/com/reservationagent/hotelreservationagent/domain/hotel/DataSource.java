package com.reservationagent.hotelreservationagent.domain.hotel;

/** Origin of a hotel record, with the licence that governs it. */
public enum DataSource {
    OPENSTREETMAP("ODbL-1.0"),
    OVERTURE_MAPS("CDLA-Permissive-2.0"),
    FOURSQUARE_OS_PLACES("Apache-2.0"),
    WIKIDATA("CC0-1.0"),
    GOOGLE_PLACES("Google Maps Platform Terms"),
    PHONE_QUOTE("Internal");

    private final String licence;

    DataSource(String licence) {
        this.licence = licence;
    }

    public String licence() {
        return licence;
    }
}
