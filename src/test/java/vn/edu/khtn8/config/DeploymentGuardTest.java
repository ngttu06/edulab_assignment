package vn.edu.khtn8.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import vn.edu.khtn8.common.config.AppProperties;
import static org.junit.jupiter.api.Assertions.*;

class DeploymentGuardTest {
    private MockEnvironment remote() {
        var env=new MockEnvironment().withProperty("spring.datasource.url","jdbc:mysql://db.example.test/edulab?sslMode=VERIFY_IDENTITY");
        env.setActiveProfiles("remote");return env;
    }
    @Test void runtimeManagedAiDoesNotRequireLegacyPlaintextCredentialAtStartup() {
        var properties=new AppProperties();
        assertDoesNotThrow(()->new DeploymentGuard(properties,remote()).validate());
        properties.getAi().setProvider("stub");
        assertThrows(IllegalStateException.class,()->new DeploymentGuard(properties,remote()).validate());
    }
    @Test void remoteRejectsInsecureCookiesScanBypassAndMixedProfileStub() {
        var properties=new AppProperties();
        properties.getCookie().setSecure(false);
        assertThrows(IllegalStateException.class,()->new DeploymentGuard(properties,remote()).validate());
        properties.getCookie().setSecure(true);properties.getUpload().setDevScanBypass(true);
        assertThrows(IllegalStateException.class,()->new DeploymentGuard(properties,remote()).validate());
        properties.getUpload().setDevScanBypass(false);properties.getAi().setProvider("stub");
        var env=remote();env.setActiveProfiles("local","remote");
        assertThrows(IllegalStateException.class,()->new DeploymentGuard(properties,env).validate());
    }
    @Test void remoteRequiresVerifiedDatabaseTlsAndSeparatelyRunMigrations() {
        var env=remote();var properties=new AppProperties();
        env.withProperty("spring.datasource.url","jdbc:mysql://db.example.test/edulab?sslMode=REQUIRED");
        assertThrows(IllegalStateException.class,()->new DeploymentGuard(properties,env).validate());
        env.withProperty("spring.datasource.url","jdbc:mysql://db.example.test/edulab?sslMode=VERIFY_IDENTITY")
                .withProperty("spring.flyway.enabled","true");
        assertThrows(IllegalStateException.class,()->new DeploymentGuard(properties,env).validate());
    }
    @Test void localOnlyPermitsStubButStillValidatesTimeouts() {
        var env=new MockEnvironment();env.setActiveProfiles("local");
        var properties=new AppProperties();properties.getCookie().setSecure(false);properties.getAi().setProvider("stub");
        assertDoesNotThrow(()->new DeploymentGuard(properties,env).validate());
        properties.getAi().setReadTimeoutSeconds(121);
        assertThrows(IllegalStateException.class,()->new DeploymentGuard(properties,env).validate());
    }
}