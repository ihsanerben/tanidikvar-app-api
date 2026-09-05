package com.tanidikvar.api.health.service;

import com.tanidikvar.api.health.dto.HealthResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HealthService {
    private final JdbcTemplate jdbc;
    public HealthService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public HealthResponse check() {
        jdbc.queryForObject("SELECT 1", Integer.class);
        return new HealthResponse("ok", "up");
    }
}
