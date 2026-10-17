package com.reservationagent.hotelreservationagent.application.service.geography;

import com.reservationagent.hotelreservationagent.application.port.in.SearchMunicipalitiesUseCase;
import com.reservationagent.hotelreservationagent.application.port.out.MunicipalityRepository;
import com.reservationagent.hotelreservationagent.domain.geography.Municipality;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MunicipalityService implements SearchMunicipalitiesUseCase {

    private static final int MAX_LIMIT = 20;

    private final MunicipalityRepository municipalities;

    public MunicipalityService(MunicipalityRepository municipalities) {
        this.municipalities = municipalities;
    }

    @Override
    public List<Municipality> search(String prefix, int limit) {
        if (prefix == null || prefix.isBlank()) {
            return List.of();
        }
        return municipalities.searchByPrefix(prefix.trim(), Math.clamp(limit, 1, MAX_LIMIT));
    }
}
