package net.kgomc.zelda.database.migration;

import net.kgomc.zelda.database.connection.ZeldaDataSource;
import net.kgomc.zelda.database.locking.LockManager;
import net.kgomc.zelda.database.locking.ZeldaLock;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs pending {@link IMigration}s in ascending version order.
 *
 * <p>Maintains a {@code zelda_migrations} table to track which versions
 * have already been applied. Each migration runs inside its own transaction
 * and is rolled back on failure — the runner stops immediately on the first
 * failing migration to prevent a broken half-applied state.</p>
 *
 * <p>The entire {@link #run()} call is protected by a distributed advisory lock
 * ({@code "zelda:migration"}) so that two server nodes starting simultaneously
 * cannot both attempt to apply the same migrations.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * db.migrations()
 *     .register(new V1_CreatePlayersTable())
 *     .register(new V2_AddCoinsIndex())
 *     .run();
 * }</pre>
 */
public final class MigrationRunner {

    private static final String HISTORY_TABLE  = "zelda_migrations";
    private static final String MIGRATION_LOCK = "zelda:migration";

    /** Seconds to wait for the migration lock before giving up. */
    private static final int LOCK_TIMEOUT_SECONDS = 60;

    private final ZeldaDataSource  dataSource;
    private final LockManager      lockManager;
    private final Logger           logger;
    private final List<IMigration> migrations = new ArrayList<>();

    // -----------------------------------------------------------------------
    // Construction
    // -----------------------------------------------------------------------

    private MigrationRunner(ZeldaDataSource dataSource, LockManager lockManager, Logger logger) {
        this.dataSource  = dataSource;
        this.lockManager = lockManager;
        this.logger      = logger;
    }

    public static MigrationRunner create(ZeldaDataSource dataSource, LockManager lockManager, Logger logger) {
        return new MigrationRunner(dataSource, lockManager, logger);
    }

    // -----------------------------------------------------------------------
    // Registration
    // -----------------------------------------------------------------------

    /**
     * Registers a migration. Can be chained.
     *
     * @throws IllegalArgumentException if a migration with the same version is already registered
     */
    public MigrationRunner register(IMigration migration) {
        boolean duplicate = migrations.stream()
                .anyMatch(m -> m.getVersion() == migration.getVersion());
        if (duplicate) {
            throw new IllegalArgumentException(
                    "Duplicate migration version: " + migration.getVersion()
                            + " (" + migration.getDescription() + ")"
            );
        }
        migrations.add(migration);
        return this;
    }

    /**
     * Registers multiple migrations at once.
     */
    public MigrationRunner registerAll(IMigration... migrations) {
        for (IMigration m : migrations) register(m);
        return this;
    }

    // -----------------------------------------------------------------------
    // Run
    // -----------------------------------------------------------------------

    /**
     * Acquires a distributed advisory lock, then ensures the history table exists
     * and runs all pending migrations in ascending version order.
     *
     * <p>If another node already holds the migration lock, this call blocks up to
     * {@value LOCK_TIMEOUT_SECONDS} seconds before throwing a {@link MigrationException}.
     * Once the lock is released by the first node, the second node will re-check applied
     * versions and find nothing pending — so it exits cleanly without re-running anything.</p>
     *
     * @throws MigrationException if the lock cannot be acquired or any migration fails
     */
    public void run() {
        logger.info("[Zelda/Migrations] Acquiring migration lock ('" + MIGRATION_LOCK
                + "', timeout=" + LOCK_TIMEOUT_SECONDS + "s)...");

        try (ZeldaLock lock = lockManager.advisory(MIGRATION_LOCK, LOCK_TIMEOUT_SECONDS)) {
            logger.info("[Zelda/Migrations] Lock acquired. Checking pending migrations...");
            runLocked();
        } catch (MigrationException e) {
            throw e; // already well-described
        } catch (Exception e) {
            throw new MigrationException("Failed to acquire migration lock", e);
        }
    }

    private void runLocked() {
        ensureHistoryTable();

        List<IMigration> sorted = new ArrayList<>(migrations);
        sorted.sort(Comparator.comparingInt(IMigration::getVersion));

        Set<Integer> applied = loadAppliedVersions();
        List<IMigration> pending = sorted.stream()
                .filter(m -> !applied.contains(m.getVersion()))
                .toList();

        if (pending.isEmpty()) {
            logger.info("[Zelda/Migrations] All migrations up to date (" + applied.size() + " applied).");
            return;
        }

        logger.info("[Zelda/Migrations] Running " + pending.size() + " pending migration(s)...");

        for (IMigration migration : pending) {
            runOne(migration);
        }

        logger.info("[Zelda/Migrations] All migrations completed successfully.");
    }

    // -----------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------

    private void ensureHistoryTable() {
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS %s (
                    version     INT          NOT NULL PRIMARY KEY,
                    description VARCHAR(255) NOT NULL,
                    applied_at  TIMESTAMP    NOT NULL
                )
                """.formatted(HISTORY_TABLE));
        } catch (SQLException e) {
            throw new MigrationException("Failed to create migration history table", e);
        }
    }

    private Set<Integer> loadAppliedVersions() {
        Set<Integer> versions = new HashSet<>();
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT version FROM " + HISTORY_TABLE)) {
            while (rs.next()) versions.add(rs.getInt("version"));
        } catch (SQLException e) {
            throw new MigrationException("Failed to load applied migration versions", e);
        }
        return versions;
    }

    private void runOne(IMigration migration) {
        logger.info(String.format("[Zelda/Migrations] Applying v%d: %s",
                migration.getVersion(), migration.getDescription()));

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                migration.migrate(conn, dataSource.getType());
                recordApplied(conn, migration);
                conn.commit();
                logger.info(String.format("[Zelda/Migrations] v%d applied successfully.",
                        migration.getVersion()));
            } catch (Exception e) {
                safeRollback(conn);
                throw new MigrationException(
                        "Migration v" + migration.getVersion()
                                + " (" + migration.getDescription() + ") failed — rolled back", e);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new MigrationException("Failed to obtain connection for migration v"
                    + migration.getVersion(), e);
        }
    }

    private void recordApplied(Connection conn, IMigration migration) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO " + HISTORY_TABLE + " (version, description, applied_at) VALUES (?, ?, ?)")) {
            ps.setInt(1, migration.getVersion());
            ps.setString(2, migration.getDescription());
            ps.setTimestamp(3, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        }
    }

    private void safeRollback(Connection conn) {
        try {
            conn.rollback();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[Zelda/Migrations] Rollback failed", e);
        }
    }
}