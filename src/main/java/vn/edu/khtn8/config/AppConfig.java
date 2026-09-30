package vn.edu.khtn8.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import vn.edu.khtn8.common.config.AppProperties;

@Configuration
@org.springframework.scheduling.annotation.EnableScheduling
@EnableConfigurationProperties(AppProperties.class)
public class AppConfig {
    @org.springframework.context.annotation.Bean
    org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy localMigrationsOnly(org.springframework.core.env.Environment env) {
        return flyway -> {
            boolean local = env.acceptsProfiles(org.springframework.core.env.Profiles.of("local"))
                    && !env.acceptsProfiles(org.springframework.core.env.Profiles.of("remote","prod"));
            String url = env.getProperty("spring.datasource.url", "");
            if (!local || !url.matches("jdbc:mysql://(?:localhost|127\\.0\\.0\\.1)(?::[0-9]+)?/.*"))
                throw new IllegalStateException("Automatic migrations only allowed on loopback MySQL with local-only profile");
            flyway.migrate();
        };
    }
}
