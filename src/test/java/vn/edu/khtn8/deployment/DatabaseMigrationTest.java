package vn.edu.khtn8.deployment;

import java.util.HashMap;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatabaseMigrationTest {
    Map<String, String> environment() {
        return new HashMap<>(Map.of("EDULAB_DB_URL", "jdbc:mysql://db.example.test/edulab?sslMode=VERIFY_IDENTITY&serverTimezone=UTC",
                "EDULAB_DB_USERNAME", "test", "EDULAB_DB_PASSWORD", "test-only"));
    }
    Flyway database(int pending, boolean initialized) {
        Flyway flyway = mock(Flyway.class);
        MigrationInfoService info = mock(MigrationInfoService.class);
        when(flyway.info()).thenReturn(info);
        when(info.current()).thenReturn(initialized ? mock(MigrationInfo.class) : null);
        when(info.pending()).thenReturn(new MigrationInfo[pending]);
        return flyway;
    }
    @Test void requiresTlsAndSeparateCredentials() {
        var env = environment();
        assertDoesNotThrow(() -> DatabaseMigration.validateEnvironment(env));
        for (String url : new String[]{"jdbc:mysql://db.example.test/edulab?sslMode=REQUIRED",
                "jdbc:mysql://db.example.test/edulab?sslMode=VERIFY_IDENTITY&allowPublicKeyRetrieval=true",
                "jdbc:mysql://db.example.test/edulab?sslMode=VERIFY_IDENTITY&password=unsafe",
                "jdbc:postgresql://db.example.test/edulab?sslMode=VERIFY_IDENTITY"}) {
            env.put("EDULAB_DB_URL", url);
            assertThrows(IllegalArgumentException.class, () -> DatabaseMigration.validateEnvironment(env));
        }
    }
    @Test void rejectsMissingCredentialAndAmbiguousApproval() {
        var env = environment();
        env.put("EDULAB_DB_PASSWORD", " ");
        assertThrows(IllegalArgumentException.class, () -> DatabaseMigration.validateEnvironment(env));
        var invalid = environment();invalid.put("EDULAB_MIGRATE_ON_DEPLOY", "yes");
        assertThrows(IllegalArgumentException.class, () -> DatabaseMigration.validateEnvironment(invalid));
    }
    @Test void currentSchemaOnlyValidates() {
        Flyway flyway = database(0, true);
        DatabaseMigration.prepareDatabase(flyway, false);
        verify(flyway).validate();verify(flyway, never()).migrate();
    }
    @Test void pendingMigrationsRequireExplicitApproval() {
        Flyway flyway = database(2, true);
        assertThrows(IllegalStateException.class, () -> DatabaseMigration.prepareDatabase(flyway, false));
        verify(flyway, never()).migrate();
    }
    @Test void approvedDeploymentMigratesSeparately() {
        Flyway flyway = database(2, true);
        DatabaseMigration.prepareDatabase(flyway, true);
        var order = inOrder(flyway);
        order.verify(flyway).validate();order.verify(flyway).info();order.verify(flyway).migrate();
    }
    @Test void neverSeedsNewCloudDatabaseEvenWithApproval() {
        Flyway flyway = database(16, false);
        assertThrows(IllegalStateException.class, () -> DatabaseMigration.prepareDatabase(flyway, true));
        verify(flyway, never()).migrate();verify(flyway, never()).baseline();verify(flyway, never()).clean();
    }
    @Test void validationFailureNeverTriggersMigrationOrRepair() {
        Flyway flyway = database(2, true);
        doThrow(new IllegalStateException("invalid history")).when(flyway).validate();
        assertThrows(IllegalStateException.class, () -> DatabaseMigration.prepareDatabase(flyway, true));
        verify(flyway, never()).info();verify(flyway, never()).migrate();verify(flyway, never()).repair();
    }
}