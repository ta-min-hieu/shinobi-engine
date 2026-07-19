package dev.kage.server.database;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;

/** Chạy migration lúc khởi động server (ADR-013 — Flyway, script ở {@code db/migration}). */
public final class FlywayMigrator {

    private FlywayMigrator() {
    }

    public static void migrate(DataSource dataSource) {
        Flyway.configure()
                .dataSource(dataSource)
                .load()
                .migrate();
    }
}
