package net.kgomc.zelda.database.module;

import com.google.gson.Gson;
import net.kgomc.zelda.core.context.ZeldaContext;
import net.kgomc.zelda.core.module.ZeldaModule;
import net.kgomc.zelda.database.config.DatabaseConfig;
import net.kgomc.zelda.database.connection.ZeldaDataSource;
import net.kgomc.zelda.database.locking.LockManager;
import net.kgomc.zelda.database.migration.MigrationRunner;
import net.kgomc.zelda.database.query.QueryRunner;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Zelda database module.
 *
 * <p>Registered via the builder:</p>
 * <pre>{@code
 * Zelda.builder()
 *     .withDatabase(pluginDataFolder.toPath().resolve("zelda/database.json"))
 *     .initialize(this);
 * }</pre>
 *
 * <h2>Running migrations on enable</h2>
 * <pre>{@code
 * public class MyPlugin extends JavaPlugin {
 *     @Override public void onEnable() {
 *         Zelda.builder()
 *             .withDatabase(configPath)
 *             .initialize(this);
 *
 *         DatabaseModule db = ZeldaContext.get()
 *             .getRegistry().find(DatabaseModule.class).orElseThrow();
 *
 *         db.migrations()
 *             .register(new V1_CreatePlayersTable())
 *             .register(new V2_AddCoinsIndex())
 *             .run();
 *     }
 * }
 * }</pre>
 */
public final class DatabaseModule implements ZeldaModule {

    private final Path configPath;

    private DatabaseConfig  config;
    private ZeldaDataSource dataSource;
    private QueryRunner     runner;
    private LockManager     lockManager;
    private MigrationRunner migrationRunner;
    private Logger          logger;

    public DatabaseModule(Path configPath) {
        this.configPath = configPath;
    }

    // -----------------------------------------------------------------------
    // ZeldaModule
    // -----------------------------------------------------------------------

    @Override
    public String getName() { return "database"; }

    @Override
    public void onEnable(ZeldaContext context) {
        context.getLogger().info("[Zelda/DB] Loading config from: " + configPath);
        this.logger = context.getLogger();
        try {
            config = DatabaseConfig.load(configPath);
        } catch (IOException e) {
            throw new RuntimeException("[Zelda/DB] Failed to load database.json", e);
        }

        context.getLogger().info("[Zelda/DB] Connecting to " + config.getType()
                + " @ " + config.buildJdbcUrl());

        this.dataSource  = new ZeldaDataSource(config);
        this.runner      = new QueryRunner(dataSource, context.getLogger());
        this.lockManager = new LockManager(dataSource, config);
        this.migrationRunner =  MigrationRunner.create(dataSource, lockManager, context.getLogger());

        context.getLogger().info("[Zelda/DB] Connection pool ready.");
    }

    @Override
    public void onDisable() {
        if(this.runner != null) {
            try {
                this.runner.close();
            } catch (Exception e) {
                logger.severe("[Zelda/DB] Failed to close QueryRunner: [ " + e.getMessage() + "]");
            }
        }
        if (dataSource != null) dataSource.close();
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /** The query runner for all SQL operations. */
    public QueryRunner getRunner() {
        assertEnabled();
        return runner;
    }

    /** The lock manager for advisory and row-level locking. */
    public LockManager getLockManager() {
        assertEnabled();
        return lockManager;
    }

    /**
     * Returns a pre-configured {@link MigrationRunner} bound to this module's
     * data source. Register your migrations and call {@code .run()}.
     */
    public MigrationRunner migrations() {
        assertEnabled();
        return migrationRunner;
    }

    /** Raw data source — for advanced use cases (e.g. external migration tools). */
    public ZeldaDataSource getDataSource() {
        assertEnabled();
        return dataSource;
    }

    private void assertEnabled() {
        if (dataSource == null)
            throw new IllegalStateException("DatabaseModule is not yet enabled.");
    }
}