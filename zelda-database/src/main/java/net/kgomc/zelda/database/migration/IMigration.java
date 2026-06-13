package net.kgomc.zelda.database.migration;

import net.kgomc.zelda.database.config.DatabaseType;
import net.kgomc.zelda.database.conventions.IConventions;

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
     * A constant representing PostgreSQL-specific naming conventions
     * defined by the {@link IConventions} interface.
     *
     * This constant provides methods for generating table and column names
     * that adhere to PostgreSQL database standards, including transformations
     * based on schemas, entity classes, and other parameters.
     *
     * It is typically used to enforce consistent and database-appropriate
     * naming conventions in migrations, schemas, and SQL interactions
     * within PostgreSQL databases.
     */
    IConventions POSTGRES = IConventions.POSTGRES;

    /**
     * A constant representing the naming conventions to be used, as defined by
     * the {@link IConventions} interface. This constant is assigned to the
     * PostgreSQL-specific implementation ({@link IConventions#POSTGRES}).
     *
     * This value is intended as the default set of naming conventions for components
     * that rely on database schema and table/column name generation. It ensures consistency
     * in naming across migrations, schemas, and other database-related operations
     * when working with PostgreSQL databases.
     */
    IConventions CONVENTIONS = IConventions.POSTGRES;

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