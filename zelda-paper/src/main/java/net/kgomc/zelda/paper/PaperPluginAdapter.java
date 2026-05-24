package net.kgomc.zelda.paper;

import net.kgomc.zelda.core.context.RuntimeKind;
import net.kgomc.zelda.core.context.ZeldaPlugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Adapts a Bukkit/Paper {@link JavaPlugin} to the {@link ZeldaPlugin} interface.
 *
 * <p>Name, data folder, and logger are read directly from the plugin — no manual
 * configuration needed. All three can be overridden if required.</p>
 *
 * <h2>Default usage</h2>
 * <pre>{@code
 * Zelda.builder()
 *     .initialize(new PaperPluginAdapter(this));
 * }</pre>
 *
 * <h2>With overrides</h2>
 * <pre>{@code
 * Zelda.builder()
 *     .initialize(new PaperPluginAdapter(this)
 *         .withName("MyCustomName")
 *         .withDataFolder(someOtherPath)
 *         .withLogger(myCustomLogger));
 * }</pre>
 */
public final class PaperPluginAdapter implements ZeldaPlugin {

    private final JavaPlugin plugin;

    private String name;
    private Path   dataFolder;
    private Logger logger;

    public PaperPluginAdapter(JavaPlugin plugin) {
        if (plugin == null) throw new IllegalArgumentException("plugin must not be null");
        this.plugin     = plugin;
        // Defaults — pulled straight from the plugin
        this.name       = plugin.getName();
        this.dataFolder = plugin.getDataFolder().toPath();
        this.logger     = plugin.getLogger();
    }

    // -----------------------------------------------------------------------
    // Overrides (optional, fluent)
    // -----------------------------------------------------------------------

    public PaperPluginAdapter withName(String name) {
        this.name = name;
        return this;
    }

    public PaperPluginAdapter withDataFolder(Path dataFolder) {
        this.dataFolder = dataFolder;
        return this;
    }

    public PaperPluginAdapter withLogger(Logger logger) {
        this.logger = logger;
        return this;
    }

    @Override public String getName()      { return name; }
    @Override public Logger getLogger()    { return logger; }
    @Override public Path getDataFolder()  { return dataFolder; }

    @Override
    public RuntimeKind getRuntimeKind() {
        return RuntimeKind.SERVER;
    }

    @Override
    public void runSync(Runnable task) {
        plugin.getServer().getScheduler().runTask(plugin, task);
    }

    @Override
    public net.kgomc.zelda.core.context.ZeldaTask runTaskTimer(Runnable task, long delayTicks, long periodTicks) {
        org.bukkit.scheduler.BukkitTask bukkitTask =
                plugin.getServer().getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        return new net.kgomc.zelda.core.context.ZeldaTask() {
            @Override public void cancel()          { bukkitTask.cancel(); }
            @Override public boolean isCancelled()  { return bukkitTask.isCancelled(); }
        };
    }


    /** Returns the underlying {@link JavaPlugin} for Paper-specific operations. */
    public JavaPlugin unwrap() { return plugin; }
}