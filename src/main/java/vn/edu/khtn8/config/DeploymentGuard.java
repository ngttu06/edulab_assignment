package vn.edu.khtn8.config;

import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import vn.edu.khtn8.common.config.AppProperties;

@Component
public class DeploymentGuard {
    private final AppProperties properties;
    private final Environment environment;
    public DeploymentGuard(AppProperties properties, Environment environment) { this.properties = properties; this.environment = environment; }
    @PostConstruct
    void validate() {
        boolean localOnly = environment.acceptsProfiles(Profiles.of("local")) && !environment.acceptsProfiles(Profiles.of("remote", "prod"));
        if (!localOnly && (!properties.getCookie().isSecure() || properties.getUpload().isDevScanBypass()))
            throw new IllegalStateException("Insecure cookies and scan bypass require local-only profile");
        if (!java.util.Set.of("Lax", "Strict").contains(properties.getCookie().getSameSite()))
            throw new IllegalStateException("Cookie SameSite must be Lax or Strict");
        var upload = properties.getUpload();
        if (upload.getMaxImageBytes() <= 0 || upload.getMaxImageBytes() > 5 * 1024 * 1024 ||
                upload.getMaxPdfBytes() <= 0 || upload.getMaxPdfBytes() > 10 * 1024 * 1024)
            throw new IllegalStateException("Invalid upload size limits");
        if (properties.getAi().getDailyRequestLimit() < 1 || properties.getAi().getDailyRequestLimit() > 100)
            throw new IllegalStateException("AI daily limit must be between 1 and 100");
        var ai = properties.getAi();
        if (!java.util.Set.of("gatecheap", "stub").contains(ai.getProvider()) || (!localOnly && ai.getProvider().equals("stub")))
            throw new IllegalStateException("AI provider must be gatecheap; stub requires local-only profile");
        // AI is enabled through the admin panel only after an encrypted key is saved.
        if (ai.getConnectTimeoutSeconds() < 1 || ai.getConnectTimeoutSeconds() > 30 || ai.getReadTimeoutSeconds() < 1 || ai.getReadTimeoutSeconds() > 120
                || ai.getMaxOutputTokens() < 100 || ai.getMaxOutputTokens() > 4000 || ai.getModel().isBlank())
            throw new IllegalStateException("Invalid AI timeout, model or output limit");
        if (environment.acceptsProfiles(Profiles.of("remote", "prod"))) {
            String url = environment.getProperty("spring.datasource.url", "");
            if (!url.matches(".*[?&]sslMode=VERIFY_IDENTITY(?:&.*)?"))
                throw new IllegalStateException("Remote MySQL requires sslMode=VERIFY_IDENTITY");
            if (environment.getProperty("spring.flyway.enabled", Boolean.class, false))
                throw new IllegalStateException("Run remote migrations separately, not at application startup");
        }
    }
}
