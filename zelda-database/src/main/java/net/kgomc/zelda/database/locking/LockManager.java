package net.kgomc.zelda.database.locking;

import net.kgomc.zelda.database.config.DatabaseConfig;
import net.kgomc.zelda.database.config.DatabaseType;
import net.kgomc.zelda.database.connection.ZeldaDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;

/**
 * Public API for database-backed locking.
 *
 * <p>Obtain an instance from {@link net.kgomc.zelda.database.module.DatabaseModule#getLockManager()}.</p>
 *
 * <h2>Advisory lock (distributed mutex)</h2>
 * <pre>{@code
 * // Blocks up to 10 seconds, then throws if not acquired
 * try (ZeldaLock lock = lockManager.advisory("zelda:daily-reward", 10)) {
 *     // Only one process/thread holds this lock at a time
 *     rewardService.runDailyRewards();
 * }
 *
 * // Non-blocking — returns empty Optional immediately if lock is taken
 * lockManager.tryAdvisory("zelda:sync", 0).ifPresent(lock -> {
 *     try (lock) { sync(); }
 * });
 * }</pre>
 *
 * <h2>Row lock (SELECT FOR UPDATE)</h2>
 * <pre>{@code
 * runner.transaction(conn -> {
 *     // Lock the row before reading + writing — prevents lost updates
 *     lockManager.forUpdate(conn, "players", "uuid = ?", playerId);
 *     int coins = runner.queryOne(conn, "SELECT coins FROM players WHERE uuid = ?", ...);
 *     runner.update(conn, "UPDATE players SET coins = ? WHERE uuid = ?", coins + 100, playerId);
 * });
 * }</pre>
 */
public final class LockManager {

    private final ZeldaDataSource dataSource;
    private final LockStrategy strategy;

    public LockManager(ZeldaDataSource dataSource, DatabaseConfig config) {
        this.dataSource = dataSource;
        this.strategy   = strategyFor(config.getType());
    }

    // -----------------------------------------------------------------------
    // Advisory locks
    // -----------------------------------------------------------------------

    /**
     * Acquires an advisory lock, waiting up to {@code timeoutSeconds}.
     *
     * @param key           lock identifier — use a namespaced string, e.g. {@code "zelda:migration"}
     * @param timeoutSeconds seconds to wait; 0 = non-blocking
     * @return a held {@link ZeldaLock} — MUST be closed in a try-with-resources
     * @throws LockException if the lock cannot be acquired within the timeout
     */
    public ZeldaLock advisory(String key, int timeoutSeconds) {
        try {
            // Advisory locks need a dedicated connection held for the lock's lifetime
            Connection conn = dataSource.getConnection();
            boolean acquired = strategy.tryAcquire(conn, key, timeoutSeconds);
            if (!acquired) {
                conn.close();
                throw new LockException("Could not acquire advisory lock '" + key
                        + "' within " + timeoutSeconds + "s");
            }
            return new ZeldaLock(key, conn, strategy);
        } catch (LockException e) {
            throw e;
        } catch (Exception e) {
            throw new LockException("Failed to acquire advisory lock: " + key, e);
        }
    }

    /**
     * Attempts a non-blocking advisory lock.
     *
     * @return a held {@link ZeldaLock} wrapped in {@link java.util.Optional}, or empty if unavailable
     */
    public java.util.Optional<ZeldaLock> tryAdvisory(String key) {
        try {
            Connection conn = dataSource.getConnection();
            boolean acquired = strategy.tryAcquire(conn, key, 0);
            if (!acquired) {
                conn.close();
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(new ZeldaLock(key, conn, strategy));
        } catch (Exception e) {
            throw new LockException("Failed to try advisory lock: " + key, e);
        }
    }

    // -----------------------------------------------------------------------
    // Row locking (SELECT FOR UPDATE)
    // -----------------------------------------------------------------------

    /**
     * Issues a {@code SELECT ... FOR UPDATE} to lock matching rows for the
     * duration of the current transaction.
     *
     * <p>Must be called inside a transaction (i.e. inside a
     * {@link net.kgomc.zelda.database.query.QueryRunner#transaction} callback).</p>
     *
     * @param conn   the transactional connection
     * @param table  table to lock rows in
     * @param where  WHERE clause without the keyword, e.g. {@code "uuid = ?"}
     * @param params bind parameters for the WHERE clause
     */
    public void forUpdate(Connection conn, String table, String where, Object... params) {
        try {
            strategy.lockRows(conn, table, where, params);
        } catch (Exception e) {
            throw new LockException("SELECT FOR UPDATE failed on " + table + " WHERE " + where, e);
        }
    }

    // -----------------------------------------------------------------------
    // Internal
    // -----------------------------------------------------------------------

    private static LockStrategy strategyFor(DatabaseType type) {
        return switch (type) {
            case POSTGRESQL -> new PostgresLockStrategy();
            case MYSQL      -> new MySQLLockStrategy();
            case SQLITE     -> new SQLiteLockStrategy();
        };
    }
}