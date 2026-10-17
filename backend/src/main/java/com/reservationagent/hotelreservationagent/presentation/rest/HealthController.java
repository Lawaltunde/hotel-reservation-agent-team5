package com.reservationagent.hotelreservationagent.presentation.rest;

import com.reservationagent.hotelreservationagent.presentation.dto.HealthResponse;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final String serviceName;

    public HealthController(@Value("${spring.application.name}") String serviceName) {
        this.serviceName = serviceName;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("UP", serviceName, Instant.now());
    }
}
