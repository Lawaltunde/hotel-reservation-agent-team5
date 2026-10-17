package com.reservationagent.hotelreservationagent.presentation.rest;

import com.reservationagent.hotelreservationagent.application.port.out.DatabaseHealth;
import com.reservationagent.hotelreservationagent.presentation.dto.HealthResponse;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 200 with status UP when the app and its database work; 503 with status DOWN otherwise. */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final String serviceName;
    private final DatabaseHealth database;

    public HealthController(@Value("${spring.application.name}") String serviceName, DatabaseHealth database) {
        this.serviceName = serviceName;
        this.database = database;
    }

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        boolean dbUp = database.isUp();
        HealthResponse body = new HealthResponse(dbUp ? "UP" : "DOWN", serviceName, Instant.now(), dbUp ? "UP" : "DOWN");
        return ResponseEntity.status(dbUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
