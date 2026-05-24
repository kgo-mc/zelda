package net.kgomc.zelda.database.locking;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * PostgreSQL locking strategy.
 *
 * <ul>
 *   <li>Advisory: {@code pg_try_advisory_lock(bigint)} / {@code pg_advisory_unlock(bigint)}</li>
 *   <li>Row: {@code SELECT ... FOR UPDATE}</li>
 * </ul>
 *
 * <p>PostgreSQL advisory locks are integer-keyed. We derive a stable {@code long}
 * from the string key using a simple FNV-1a hash — fast, uniform, no deps.</p>
 *
 * <p>The {@code xact} variant ({@code pg_try_advisory_xact_lock}) would auto-release
 * on transaction close, but we use the session variant so locks survive across
 * multiple statements and are released explicitly via {@link #release}.</p>
 */
class PostgresLockStrategy implements LockStrategy {

    @Override
    public boolean tryAcquire(Connection conn, String key, int timeoutSeconds) throws Exception {
        long lockId = hash(key);

        if (timeoutSeconds == 0) {
            // Non-blocking: pg_try_advisory_lock returns boolean immediately
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT pg_try_advisory_lock(?)")) {
                ps.setLong(1, lockId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && rs.getBoolean(1);
                }
            }
        } else {
            // Blocking with timeout: loop pg_try_advisory_lock with sleep
            long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000L);
            while (System.currentTimeMillis() < deadline) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT pg_try_advisory_lock(?)")) {
                    ps.setLong(1, lockId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next() && rs.getBoolean(1)) return true;
                    }
                }
                Thread.sleep(100);
            }
            return false;
        }
    }

    @Override
    public void release(Connection conn, String key) throws Exception {
        long lockId = hash(key);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT pg_advisory_unlock(?)")) {
            ps.setLong(1, lockId);
            ps.executeQuery(); // result (bool) intentionally ignored
        }
    }

    @Override
    public void lockRows(Connection conn, String table, String where, Object... params) throws Exception {
        String sql = "SELECT 1 FROM " + table + " WHERE " + where + " FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            ps.executeQuery();
        }
    }

    // -----------------------------------------------------------------------
    // FNV-1a 64-bit hash  (key → stable long for pg_advisory_lock)
    // -----------------------------------------------------------------------

    static long hash(String key) {
        long h = 0xcbf29ce484222325L;
        for (byte b : key.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            h ^= (b & 0xFFL);
            h *= 0x00000100000001B3L;
        }
        return h;
    }
}