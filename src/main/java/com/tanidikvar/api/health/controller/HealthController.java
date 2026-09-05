package com.tanidikvar.api.health.controller;

import com.tanidikvar.api.health.dto.HealthResponse;
import com.tanidikvar.api.health.service.HealthService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    private final HealthService health;
    public HealthController(HealthService health) { this.health = health; }

    @GetMapping("/api/health")
    @Operation(summary = "Uygulama ve veritabanı bağlantı durumu")
    public HealthResponse health() { return health.check(); }
}
