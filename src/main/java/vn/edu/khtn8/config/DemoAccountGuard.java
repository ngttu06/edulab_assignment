package vn.edu.khtn8.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@DependsOnDatabaseInitialization
public class DemoAccountGuard {
    private final Environment environment;
    private final JdbcTemplate jdbc;
    public DemoAccountGuard(Environment environment, JdbcTemplate jdbc) { this.environment = environment; this.jdbc = jdbc; }
    @PostConstruct
    void rejectDemoPasswordsOutsideLocal() {
        if (environment.acceptsProfiles(Profiles.of("local")) && !environment.acceptsProfiles(Profiles.of("remote", "prod"))) return;
        int count = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE enabled=TRUE AND password_hash=?", Integer.class,
                "$2y$10$JBMcsVrzfuorYdoRnb863uRpbjASC7Le1YpJseW2YkIq6UxGGriqC");
        if (count > 0) throw new IllegalStateException("Demo accounts must be disabled or given unique passwords before running outside local profile; see README");
    }
}
