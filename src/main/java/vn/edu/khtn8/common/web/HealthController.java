package vn.edu.khtn8.common.web;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Minimal readiness probe without exposing credentials, versions or DB details. */
@RestController
public class HealthController {
    private final JdbcTemplate jdbc;
    public HealthController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/actuator/health")
    public ResponseEntity<Map<String, String>> health() {
        try {
            Integer value = jdbc.queryForObject("SELECT 1", Integer.class);
            if (Integer.valueOf(1).equals(value)) return ResponseEntity.ok(Map.of("status", "UP"));
        } catch (org.springframework.dao.DataAccessException ignored) {
            // A failed probe must not leak SQL errors or connection information.
        }
        return ResponseEntity.status(503).body(Map.of("status", "DOWN"));
    }
}