package net.kgomc.zelda.database.migration;

import net.kgomc.zelda.database.config.DatabaseType;

import java.sql.Connection;

/**
 * A single, versioned database migration.
 *
 * <p>Implement one class per migration. The {@link MigrationRunner} executes
 * them in ascending {@link #getVersion()} order, skipping any that have
 * already been applied.</p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * public class V1_CreatePlayersTable implements IMigration {
 *
 *     @Override public int getVersion() { return 1; }
 *
 *     @Override public String getDescription() { return "Create players table"; }
 *
 *     @Override
 *     public void migrate(Connection conn, DatabaseType type) throws Exception {
 *         if(type == DatabaseType.SQLITE) return; // SQLite doesn't support CREATE TABLE IF NOT EXISTS
 *
 *         try (Statement st = conn.createStatement()) {
 *             st.execute("""
 *                 CREATE TABLE IF NOT EXISTS players (
 *                     uuid    VARCHAR(36) PRIMARY KEY,
 *                     name    VARCHAR(16) NOT NULL,
 *                     coins   INT         NOT NULL DEFAULT 0,
 *                     created TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
 *                 )
 *             """);
 *         }
 *     }
 * }
 * }</pre>
 */
public interface IMigration {

    /**
     * Monotonically increasing version number for this migration.
     * Migrations are executed in ascending order; no two migrations may share
     * the same version.
     */
    int getVersion();

    /**
     * Human-readable description — stored in the migration history table
     * and printed in logs.
     */
    String getDescription();

    /**
     * Executes the migration on the supplied connection.
     *
     * <p>The connection is already inside a transaction. Throw any exception
     * to trigger a rollback and abort the migration run.</p>
     *
     * @param conn an active, transaction-wrapped connection
     * @throws Exception if anything goes wrong — the runner will roll back
     */
    void migrate(Connection conn, DatabaseType type) throws Exception;
}