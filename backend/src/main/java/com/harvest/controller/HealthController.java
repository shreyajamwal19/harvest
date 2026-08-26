package com.harvest.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Previously always reported "UP" unconditionally, with no dependency check at all - a load
 * balancer or orchestrator's health/readiness probe would keep routing traffic here even during
 * a full database outage, since the backend can't actually serve any real request in that state
 * but this endpoint would say otherwise. Now does a real (cheap) connectivity check against the
 * database, the one hard dependency every actual request needs, and reports DOWN + 503 if it's
 * unreachable - honest status, not just a liveness ping.
 */
@Slf4j
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private static final int VALIDATION_TIMEOUT_SECONDS = 3;

    private final DataSource dataSource;

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        boolean databaseUp = isDatabaseReachable();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", databaseUp ? "UP" : "DOWN");
        body.put("service", "harvest-backend");
        body.put("database", databaseUp ? "UP" : "DOWN");
        body.put("timestamp", Instant.now().toString());

        return databaseUp
                ? ResponseEntity.ok(body)
                : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    private boolean isDatabaseReachable() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(VALIDATION_TIMEOUT_SECONDS);
        } catch (SQLException e) {
            log.warn("[health] database connectivity check failed: {}", e.getMessage());
            return false;
        }
    }
}
