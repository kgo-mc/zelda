package net.kgomc.zelda.outbox.migration;

import net.kgomc.zelda.database.config.DatabaseType;
import net.kgomc.zelda.database.migration.IMigration;

import java.sql.Connection;
import java.sql.Statement;

/**
 * Creates the {@code zelda_outbox} and {@code zelda_outbox_dead} tables.
 *
 * <p>On PostgreSQL, the payload column uses {@code JSONB} for native JSON
 * indexing and querying. On MySQL and SQLite it falls back to {@code TEXT}.</p>
 *
 * <p>This migration is registered automatically by {@link net.kgomc.zelda.outbox.module.OutboxModule}
 * via {@code LifecycleHook.afterAllEnabled()} — you do not need to register it manually.</p>
 */
public final class OutboxMigration implements IMigration {

    private final String schema;

    public OutboxMigration(String schema) {
        this.schema = schema;
    }

    @Override
    public int getVersion() { return 9000; }

    @Override
    public String getDescription() { return "Create zelda_outbox and zelda_outbox_dead tables"; }

    @Override
    public void migrate(Connection conn, DatabaseType type) throws Exception {
        String payloadColType = type == DatabaseType.POSTGRESQL ? "JSONB" : "TEXT";

        try (Statement st = conn.createStatement()) {

            // Main outbox table
            st.execute("""
                CREATE TABLE IF NOT EXISTS "%s".zelda_outbox (
                    id           VARCHAR(36)     NOT NULL PRIMARY KEY,
                    event_type   VARCHAR(255)    NOT NULL,
                    payload      %s              NOT NULL,
                    status       VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
                    attempts     INT             NOT NULL DEFAULT 0,
                    max_attempts INT             NOT NULL DEFAULT 3,
                    created_at   TIMESTAMP       NOT NULL,
                    process_at   TIMESTAMP       NOT NULL,
                    processed_at TIMESTAMP
                )
                """.formatted(
                        schema,
                        payloadColType));

            // Index for efficient polling — pending events due for processing
            st.execute("""
                CREATE INDEX IF NOT EXISTS idx_outbox_poll
                    ON "%s".zelda_outbox (status, process_at)
                """
                    .formatted(schema)
            );

            // Dead letter table
            st.execute("""
                CREATE TABLE IF NOT EXISTS "%s".zelda_outbox_dead (
                    id           VARCHAR(36)     NOT NULL PRIMARY KEY,
                    event_type   VARCHAR(255)    NOT NULL,
                    payload      %s              NOT NULL,
                    attempts     INT             NOT NULL,
                    max_attempts INT             NOT NULL,
                    created_at   TIMESTAMP       NOT NULL,
                    failed_at    TIMESTAMP       NOT NULL,
                    last_error   TEXT
                )
                """.formatted(
                        schema,
                        payloadColType
            ));
        }
    }

}