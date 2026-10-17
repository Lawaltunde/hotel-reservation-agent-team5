package com.reservationagent.hotelreservationagent.application.port.in;

import com.reservationagent.hotelreservationagent.domain.geography.Municipality;

import java.util.List;

/** Autocomplete for the city field. Only Ontario municipalities are returned. */
public interface SearchMunicipalitiesUseCase {

    List<Municipality> search(String prefix, int limit);
}
