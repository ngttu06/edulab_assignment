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
    }
}
