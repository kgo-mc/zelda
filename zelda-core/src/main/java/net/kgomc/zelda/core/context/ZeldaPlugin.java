package net.kgomc.zelda.core.context;

import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Platform-agnostic representation of the host plugin.
 *
 * <p>Abstracts over Bukkit/Paper's {@code JavaPlugin} and Velocity's plugin
 * system so that {@code zelda-core} and all feature modules remain
 * platform-independent.</p>
 *
 * <p>Implementations are provided by the platform adapter modules:</p>
 * <ul>
 *   <li>{@code zelda-paper} → {@code PaperPluginAdapter(JavaPlugin)}</li>
 *   <li>{@code zelda-velocity} → {@code VelocityPluginAdapter(pluginContainer, logger, dataDir)}</li>
 * </ul>
 */
public interface ZeldaPlugin {

    /** The plugin's name as declared in its descriptor (e.g. {@code plugin.yml}). */
    String getName();

    /**
     * The plugin's logger.
     * All Zelda modules log through this.
     */
    Logger getLogger();

    /**
     * The plugin's data directory.
     * Used by config and database modules to resolve file paths.
     * The directory is not guaranteed to exist — callers must create it if needed.
     */
    Path getDataFolder();

    RuntimeKind getRuntimeKind();
    /**
     * Runs a task on the main server thread.
     * On Velocity (which has no main thread concept), runs immediately on a
     * scheduler thread.
     */
    void runSync(Runnable task);

    /**
     * Schedules a repeating task.
     *
     * @param task         the runnable to execute
     * @param delayTicks   ticks before the first execution
     * @param periodTicks  ticks between subsequent executions
     * @return a {@link ZeldaTask} handle that can be used to cancel the task
     */
    ZeldaTask runTaskTimer(Runnable task, long delayTicks, long periodTicks);

}