package com.reservationagent.hotelreservationagent.slice;

import com.reservationagent.hotelreservationagent.application.port.in.SearchMunicipalitiesUseCase;
import com.reservationagent.hotelreservationagent.domain.geography.GeoPoint;
import com.reservationagent.hotelreservationagent.domain.geography.Municipality;
import com.reservationagent.hotelreservationagent.presentation.rest.MunicipalityController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MunicipalityController.class)
class MunicipalityControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SearchMunicipalitiesUseCase municipalities;

    private static Municipality municipality(String name, String zone) {
        return new Municipality(UUID.randomUUID(), name, "3506008", Set.of("alias"), new GeoPoint(45, -75),
                ZoneId.of(zone), null);
    }

    @Test
    void returnsIdNameAndTimeZoneOnly() throws Exception {
        Municipality kenora = municipality("Kenora", "America/Winnipeg");
        when(municipalities.search("ke", 10)).thenReturn(List.of(kenora));

        mvc.perform(get("/api/municipalities").param("q", "ke"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(kenora.id().toString()))
                .andExpect(jsonPath("$[0].name").value("Kenora"))
                .andExpect(jsonPath("$[0].timeZone").value("America/Winnipeg"))
                .andExpect(jsonPath("$[0].aliases").doesNotExist())
                .andExpect(jsonPath("$[0].centre").doesNotExist());
    }

    @Test
    void passesTheLimitThrough() throws Exception {
        when(municipalities.search("o", 3)).thenReturn(List.of(municipality("Ottawa", "America/Toronto")));

        mvc.perform(get("/api/municipalities").param("q", "o").param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Ottawa"));
        verify(municipalities).search("o", 3);
    }

    @Test
    void missingQueryIsAnEmptySearch() throws Exception {
        when(municipalities.search("", 10)).thenReturn(List.of());

        mvc.perform(get("/api/municipalities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        verify(municipalities).search("", 10);
    }

    @Test
    void nonNumericLimitIs400Problem() throws Exception {
        mvc.perform(get("/api/municipalities").param("q", "o").param("limit", "ten"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
