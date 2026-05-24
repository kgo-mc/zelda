package net.kgomc.zelda.database.connection;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.kgomc.zelda.database.config.DatabaseConfig;
import net.kgomc.zelda.database.config.DatabaseType;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Thin wrapper around a {@link HikariDataSource}, configured from a {@link DatabaseConfig}.
 *
 * <p>Obtain connections via {@link #getConnection()} and always close them in a
 * try-with-resources block — Hikari returns them to the pool automatically.</p>
 */
public final class ZeldaDataSource implements AutoCloseable {

    private final HikariDataSource hikari;
    private final DatabaseType type;

    public ZeldaDataSource(DatabaseConfig config) {
        HikariConfig hikariConfig = new HikariConfig();
        type = config.getType();
        hikariConfig.setJdbcUrl(config.buildJdbcUrl());
        hikariConfig.setDriverClassName(config.driverClassName());

        if (config.getUsername() != null) {
            hikariConfig.setUsername(config.getUsername());
        }
        if (config.getPassword() != null) {
            hikariConfig.setPassword(config.getPassword());
        }

        DatabaseConfig.PoolConfig pool = config.getPool();
        hikariConfig.setMaximumPoolSize(pool.getMaximumPoolSize());
        hikariConfig.setMinimumIdle(pool.getMinimumIdle());
        hikariConfig.setConnectionTimeout(pool.getConnectionTimeout());
        hikariConfig.setIdleTimeout(pool.getIdleTimeout());
        hikariConfig.setMaxLifetime(pool.getMaxLifetime());

        hikariConfig.setPoolName("zelda-" + config.getType().name().toLowerCase());

        // SQLite-specific: serialized access is safer for file-based DBs
        if (config.getType() == net.kgomc.zelda.database.config.DatabaseType.SQLITE) {
            hikariConfig.setMaximumPoolSize(1);
            hikariConfig.setConnectionInitSql("PRAGMA journal_mode=WAL;");
        }

        this.hikari = new HikariDataSource(hikariConfig);
    }

    /**
     * Borrow a connection from the pool.
     * Always use in a try-with-resources block.
     */
    public Connection getConnection() throws SQLException {
        return hikari.getConnection();
    }

    /** Whether the pool is running and healthy. */
    public boolean isRunning() {
        return hikari != null && !hikari.isClosed();
    }

    public DatabaseType getType() {
        return type;
    }

    /** Shuts the pool down — called by {@code DatabaseModule.onDisable()}. */
    @Override
    public void close() {
        if (isRunning()) {
            hikari.close();
        }
    }
}