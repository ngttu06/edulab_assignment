package vn.edu.khtn8.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MigrationGuardTest {
    @Test void remoteCannotMigrateEvenWhenFlywayEnabled() {
        var env=new MockEnvironment().withProperty("spring.datasource.url","jdbc:mysql://mysql.ngttu.com:3306/edulab_assignment");env.setActiveProfiles("remote");
        var flyway=mock(Flyway.class);
        assertThrows(IllegalStateException.class,()->new AppConfig().localMigrationsOnly(env).migrate(flyway));verifyNoInteractions(flyway);
    }
    @Test void localProfileCannotTargetRemoteServer() {
        var env=new MockEnvironment().withProperty("spring.datasource.url","jdbc:mysql://mysql.ngttu.com:3306/edulab_assignment");env.setActiveProfiles("local");
        var flyway=mock(Flyway.class);
        assertThrows(IllegalStateException.class,()->new AppConfig().localMigrationsOnly(env).migrate(flyway));verifyNoInteractions(flyway);
    }
    @Test void localLoopbackCanMigrate() {
        var env=new MockEnvironment().withProperty("spring.datasource.url","jdbc:mysql://127.0.0.1:3307/edulab_test");env.setActiveProfiles("local");
        var flyway=mock(Flyway.class);new AppConfig().localMigrationsOnly(env).migrate(flyway);verify(flyway).migrate();
    }
}