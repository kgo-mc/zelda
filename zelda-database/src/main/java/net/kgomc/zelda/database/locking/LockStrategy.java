package net.kgomc.zelda.database.locking;

import java.sql.Connection;

/**
 * Backend-specific locking strategy.
 * Each database type gets its own implementation.
 */
interface LockStrategy {

    /**
     * Attempts to acquire an advisory lock identified by {@code key}.
     *
     * @param conn    a dedicated connection that must be held open for the lock's lifetime
     * @param key     lock identifier (string — hashed to long for PostgreSQL)
     * @param timeoutSeconds how long to wait; 0 = try-only (non-blocking)
     * @return true if the lock was acquired
     */
    boolean tryAcquire(Connection conn, String key, int timeoutSeconds) throws Exception;

    /**
     * Releases the advisory lock identified by {@code key}.
     */
    void release(Connection conn, String key) throws Exception;

    /**
     * Executes a {@code SELECT ... FOR UPDATE} on the given table/where clause,
     * locking matched rows for the duration of the caller's transaction.
     *
     * @param conn      connection already inside a transaction
     * @param table     table name
     * @param where     WHERE clause (without the keyword), e.g. {@code "uuid = ?"}
     * @param params    bind parameters for the WHERE clause
     */
    void lockRows(Connection conn, String table, String where, Object... params) throws Exception;
}