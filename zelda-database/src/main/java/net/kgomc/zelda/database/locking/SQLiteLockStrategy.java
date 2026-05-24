package net.kgomc.zelda.database.locking;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * SQLite locking strategy — in-process fallback.
 *
 * <p>SQLite has no server-side advisory lock mechanism, so we use
 * {@link ReentrantLock} instances keyed by lock name. This gives mutual
 * exclusion <em>within the same JVM process</em>. For single-server Minecraft
 * deployments this is typically sufficient.</p>
 *
 * <p>{@code SELECT FOR UPDATE} is approximated with a plain {@code SELECT} —
 * SQLite's WAL mode + single-writer model makes row-level locking unnecessary
 * in practice, but the call is kept for API consistency.</p>
 */
class SQLiteLockStrategy implements LockStrategy {

    // Shared across all LockManager instances for the same JVM
    private static final ConcurrentHashMap<String, ReentrantLock> LOCKS = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(Connection conn, String key, int timeoutSeconds) throws Exception {
        ReentrantLock lock = LOCKS.computeIfAbsent(key, k -> new ReentrantLock(true));
        if (timeoutSeconds == 0) {
            return lock.tryLock();
        } else {
            return lock.tryLock(timeoutSeconds, TimeUnit.SECONDS);
        }
    }

    @Override
    public void release(Connection conn, String key) {
        ReentrantLock lock = LOCKS.get(key);
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    @Override
    public void lockRows(Connection conn, String table, String where, Object... params) throws Exception {
        // SQLite: no FOR UPDATE syntax — plain SELECT is sufficient under WAL + single-writer
        String sql = "SELECT 1 FROM " + table + " WHERE " + where;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            ps.executeQuery();
        }
    }
}