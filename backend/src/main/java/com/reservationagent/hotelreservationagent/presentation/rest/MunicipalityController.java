package com.reservationagent.hotelreservationagent.presentation.rest;

import com.reservationagent.hotelreservationagent.application.port.in.SearchMunicipalitiesUseCase;
import com.reservationagent.hotelreservationagent.presentation.dto.MunicipalityResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Autocomplete for the city field. Only Ontario municipalities are returned. */
@RestController
@RequestMapping("/api/municipalities")
public class MunicipalityController {

    private final SearchMunicipalitiesUseCase municipalities;

    public MunicipalityController(SearchMunicipalitiesUseCase municipalities) {
        this.municipalities = municipalities;
    }

    @GetMapping
    public List<MunicipalityResponse> search(@RequestParam(name = "q", defaultValue = "") String query,
                                             @RequestParam(defaultValue = "10") int limit) {
        return municipalities.search(query, limit).stream().map(MunicipalityResponse::from).toList();
    }
}
