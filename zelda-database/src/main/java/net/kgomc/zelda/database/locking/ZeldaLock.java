package net.kgomc.zelda.database.locking;

import java.sql.Connection;

/**
 * A held advisory lock. Always use in a try-with-resources block to guarantee release.
 *
 * <pre>{@code
 * try (ZeldaLock lock = lockManager.advisory("zelda:migration", 10)) {
 *     // exclusively locked section
 *     migrationRunner.run();
 * } // released automatically
 * }</pre>
 */
public final class ZeldaLock implements AutoCloseable {

    private final String key;
    private final Connection connection;  // dedicated connection held for lock's lifetime
    private final LockStrategy strategy;
    private boolean released = false;

    ZeldaLock(String key, Connection connection, LockStrategy strategy) {
        this.key        = key;
        this.connection = connection;
        this.strategy   = strategy;
    }

    /**
     * Releases the lock and returns its dedicated connection to the pool.
     * Safe to call multiple times — subsequent calls are no-ops.
     */
    @Override
    public void close() {
        if (released) return;
        released = true;
        try {
            strategy.release(connection, key);
        } catch (Exception e) {
            // best-effort release
        } finally {
            try { connection.close(); } catch (Exception ignored) {}
        }
    }

    public String getKey()      { return key; }
    public boolean isReleased() { return released; }
}