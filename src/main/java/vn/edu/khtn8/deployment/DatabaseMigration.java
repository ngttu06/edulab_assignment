package vn.edu.khtn8.deployment;

import java.util.Map;
import org.flywaydb.core.Flyway;

/** Separate deployment job: never starts Spring or the scheduled application jobs. */
public final class DatabaseMigration {
    private DatabaseMigration() {}

    public static void main(String[] args) {
        try {
            if (args.length != 0) throw new IllegalArgumentException("Migration command does not accept arguments.");
            Map<String, String> env = System.getenv();
            validateEnvironment(env);
            Flyway flyway = Flyway.configure()
                    .dataSource(env.get("EDULAB_DB_URL"), env.get("EDULAB_DB_USERNAME"), env.get("EDULAB_DB_PASSWORD"))
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(false).cleanDisabled(true)
                    .ignoreMigrationPatterns("*:pending")
                    .load();
            prepareDatabase(flyway, Boolean.parseBoolean(env.getOrDefault("EDULAB_MIGRATE_ON_DEPLOY", "false")));
        } catch (IllegalArgumentException | IllegalStateException error) {
            System.err.println(error.getMessage());
            System.exit(1);
        } catch (Exception error) {
            // Database exceptions can contain connection details: do not dump them.
            System.err.println("Database preparation failed. Check TLS, credentials, network, Flyway history and DDL permissions with your DBA.");
            System.exit(1);
        }
    }

    static void validateEnvironment(Map<String, String> env) {
        for (String key : new String[]{"EDULAB_DB_URL", "EDULAB_DB_USERNAME", "EDULAB_DB_PASSWORD"}) {
            if (env.getOrDefault(key, "").isBlank()) throw new IllegalArgumentException("Configure " + key + ".");
        }
        String url = env.get("EDULAB_DB_URL");
        if (!url.startsWith("jdbc:mysql://") || !url.matches(".*[?&]sslMode=VERIFY_IDENTITY(?:&.*)?")
                || url.matches("(?i).*[?&](?:allowPublicKeyRetrieval=true|user=[^&]*|password=[^&]*)(?:&.*)?")) {
            throw new IllegalArgumentException("Deployment migrations require MySQL sslMode=VERIFY_IDENTITY and credentials outside the JDBC URL.");
        }
        String approved = env.getOrDefault("EDULAB_MIGRATE_ON_DEPLOY", "false");
        if (!approved.equals("true") && !approved.equals("false")) {
            throw new IllegalArgumentException("EDULAB_MIGRATE_ON_DEPLOY must be true or false.");
        }
    }

    static void prepareDatabase(Flyway flyway, boolean approved) {
        // Ignore only pending migrations during validation; checksum/missing/failed
        // migrations still fail. Never repair, baseline, clean or import a database.
        flyway.validate();
        var info = flyway.info();
        if (info.current() == null) {
            throw new IllegalStateException("Database has no applied Flyway history. Provision it separately; deployment must not seed demo accounts into a new cloud database.");
        }
        int pending = info.pending().length;
        if (pending > 0 && !approved) {
            throw new IllegalStateException("Database has " + pending + " pending migration(s). Back up and review them, then set EDULAB_MIGRATE_ON_DEPLOY=true to approve the separate deployment job.");
        }
        if (pending > 0) flyway.migrate();
        System.out.println("Database schema ready; application startup migrations remain disabled.");
    }
}