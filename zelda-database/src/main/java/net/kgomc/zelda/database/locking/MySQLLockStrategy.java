package net.kgomc.zelda.database.locking;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * MySQL locking strategy.
 *
 * <ul>
 *   <li>Advisory: {@code GET_LOCK(name, timeout)} / {@code RELEASE_LOCK(name)}</li>
 *   <li>Row: {@code SELECT ... FOR UPDATE}</li>
 * </ul>
 *
 * <h3>MySQL GET_LOCK caveats handled here</h3>
 * <ul>
 *   <li><strong>64-char limit</strong> — keys longer than 64 chars are truncated with a suffix hash.</li>
 *   <li><strong>Same-connection re-entrance</strong> — MySQL silently allows acquiring the same
 *       named lock multiple times on the same connection, incrementing an internal counter.
 *       This means a second {@code GET_LOCK} on the same connection succeeds immediately with no
 *       mutual exclusion, and the lock is only fully released after an equal number of
 *       {@code RELEASE_LOCK} calls. We prevent this by tracking held lock names per connection
 *       and throwing {@link LockException} on any re-entrant attempt.</li>
 *   <li><strong>Connection-scoped</strong> — the lock is tied to the JDBC connection, not the
 *       transaction. Always use via {@link LockManager} which holds a dedicated connection.</li>
 * </ul>
 */
class MySQLLockStrategy implements LockStrategy {

    private static final int MAX_LOCK_NAME_LENGTH = 64;

    /**
     * Tracks which sanitised lock names are currently held by each connection.
     *
     * <p>WeakHashMap so that closed/GC'd connections don't leak memory.
     * Synchronised because multiple threads can share a LockManager instance.</p>
     */
    private final Map<Connection, Set<String>> heldLocks =
            Collections.synchronizedMap(new WeakHashMap<>());

    @Override
    public boolean tryAcquire(Connection conn, String key, int timeoutSeconds) throws Exception {
        String lockName = sanitiseKey(key);

        // Guard: reject re-entrant acquisition on the same connection
        Set<String> held = heldLocks.computeIfAbsent(
                conn, c -> Collections.synchronizedSet(new java.util.HashSet<>())
        );
        if (held.contains(lockName)) {
            throw new LockException(
                    "Re-entrant lock attempt detected for key '" + key + "' on the same connection. " +
                            "MySQL would silently succeed but provide no mutual exclusion — this is a bug."
            );
        }

        // GET_LOCK returns: 1 = acquired, 0 = timed out, NULL = error
        try (PreparedStatement ps = conn.prepareStatement("SELECT GET_LOCK(?, ?)")) {
            ps.setString(1, lockName);
            ps.setInt(2, timeoutSeconds);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                int result = rs.getInt(1);
                if (rs.wasNull()) throw new LockException("GET_LOCK returned NULL for key: " + key);
                boolean acquired = result == 1;
                if (acquired) held.add(lockName);
                return acquired;
            }
        }
    }

    @Override
    public void release(Connection conn, String key) throws Exception {
        String lockName = sanitiseKey(key);
        try (PreparedStatement ps = conn.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            ps.setString(1, lockName);
            ps.executeQuery(); // 1 = released, 0 = held by other, NULL = didn't exist
        } finally {
            // Always remove from tracking — even if RELEASE_LOCK fails,
            // the connection will be closed and the lock dropped server-side
            Set<String> held = heldLocks.get(conn);
            if (held != null) held.remove(lockName);
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
    // Key sanitisation — enforce MySQL's 64-char limit
    // -----------------------------------------------------------------------

    /**
     * MySQL enforces a 64-character limit on lock names.
     * If the key exceeds this, we keep the first 48 chars and append a 16-char hex hash.
     */
    static String sanitiseKey(String key) {
        if (key.length() <= MAX_LOCK_NAME_LENGTH) return key;
        String prefix = key.substring(0, 48);
        String suffix = String.format("%016x", key.hashCode() & 0xFFFFFFFFL);
        return prefix + suffix; // exactly 64 chars
    }
}