package net.kgomc.zelda.velocity;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.TaskStatus;
import net.kgomc.zelda.core.context.ZeldaPlugin;
import net.kgomc.zelda.core.context.ZeldaTask;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

/**
 * Adapts a Velocity plugin to the {@link ZeldaPlugin} interface.
 *
 * <p>Name, data directory, and logger are read directly from Velocity's
 * {@link PluginContainer} and injected values — no manual configuration needed.
 * All three can be overridden if required.</p>
 *
 * <p>Bridges Velocity's SLF4J logger to {@code java.util.logging} internally.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @Plugin(id = "myplugin", name = "MyPlugin", version = "1.0.0")
 * public class MyPlugin {
 *
 *     @Inject
 *     public MyPlugin(ProxyServer server, PluginContainer container,
 *                     Logger logger, @DataDirectory Path dataDirectory) {
 *         Zelda.builder()
 *             .initialize(new VelocityPluginAdapter(container, logger, dataDirectory, server));
 *     }
 * }
 * }</pre>
 */
public final class VelocityPluginAdapter implements ZeldaPlugin {

    private final PluginContainer container;
    private final ProxyServer     server;

    private String                   name;
    private Path                     dataFolder;
    private java.util.logging.Logger julLogger;

    public VelocityPluginAdapter(PluginContainer container, Logger slf4jLogger,
                                 Path dataDirectory, ProxyServer server) {
        if (container == null)     throw new IllegalArgumentException("container must not be null");
        if (slf4jLogger == null)   throw new IllegalArgumentException("logger must not be null");
        if (dataDirectory == null) throw new IllegalArgumentException("dataDirectory must not be null");
        if (server == null)        throw new IllegalArgumentException("server must not be null");

        this.container  = container;
        this.server     = server;
        this.name       = container.getDescription().getName()
                .orElse(container.getDescription().getId());
        this.dataFolder = dataDirectory;
        this.julLogger  = bridgeSlf4j(this.name, slf4jLogger);
    }

    // -----------------------------------------------------------------------
    // Overrides (optional, fluent)
    // -----------------------------------------------------------------------

    public VelocityPluginAdapter withName(String name) {
        this.name = name;
        return this;
    }

    public VelocityPluginAdapter withDataFolder(Path dataFolder) {
        this.dataFolder = dataFolder;
        return this;
    }

    public VelocityPluginAdapter withLogger(java.util.logging.Logger logger) {
        this.julLogger = logger;
        return this;
    }

    // -----------------------------------------------------------------------
    // ZeldaPlugin
    // -----------------------------------------------------------------------

    @Override public String getName()                    { return name; }
    @Override public java.util.logging.Logger getLogger() { return julLogger; }
    @Override public Path getDataFolder()                { return dataFolder; }

    @Override
    public net.kgomc.zelda.core.context.RuntimeKind getRuntimeKind() {
        return net.kgomc.zelda.core.context.RuntimeKind.PROXY;
    }

    /**
     * Velocity has no main thread — runs immediately inline.
     * All Velocity event handling is already async-safe.
     */
    @Override
    public void runSync(Runnable task) {
        task.run();
    }

    /**
     * Schedules a repeating task via Velocity's built-in scheduler.
     * Ticks are converted to milliseconds (50ms per tick).
     */
    @Override
    public ZeldaTask runTaskTimer(Runnable task, long delayTicks, long periodTicks) {
        Object pluginInstance = container.getInstance().orElseThrow(() ->
                new IllegalStateException("Plugin instance not available for scheduling"));

        com.velocitypowered.api.scheduler.ScheduledTask scheduled = server.getScheduler()
                .buildTask(pluginInstance, task)
                .delay(delayTicks * 50L, TimeUnit.MILLISECONDS)
                .repeat(periodTicks * 50L, TimeUnit.MILLISECONDS)
                .schedule();

        return new ZeldaTask() {
            @Override public void cancel()         { scheduled.cancel(); }
            @Override public boolean isCancelled() {
                return scheduled.status() == TaskStatus.CANCELLED;
            }
        };
    }

    /** Returns the underlying {@link PluginContainer} for Velocity-specific operations. */
    public PluginContainer unwrap() { return container; }

    // -----------------------------------------------------------------------
    // SLF4J → JUL bridge
    // -----------------------------------------------------------------------

    private static java.util.logging.Logger bridgeSlf4j(String name, Logger slf4j) {
        java.util.logging.Logger jul = java.util.logging.Logger.getLogger(name);
        jul.setUseParentHandlers(false);
        jul.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                java.util.logging.Level level = record.getLevel();
                String msg = record.getMessage();
                if      (level == java.util.logging.Level.SEVERE)  slf4j.error(msg);
                else if (level == java.util.logging.Level.WARNING) slf4j.warn(msg);
                else if (level == java.util.logging.Level.FINE)    slf4j.debug(msg);
                else                                               slf4j.info(msg);
            }
            @Override public void flush() {}
            @Override public void close() {}
        });
        return jul;
    }
}