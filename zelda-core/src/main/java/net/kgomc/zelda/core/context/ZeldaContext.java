package net.kgomc.zelda.core.context;

import net.kgomc.zelda.core.module.ModuleRegistry;
import net.kgomc.zelda.core.module.ZeldaModule;

import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Central context for the Zelda library.
 *
 * <p>Holds a {@link ZeldaPlugin} — a platform-agnostic wrapper over the host
 * plugin — and exposes it both via static singleton (ergonomic, for use deep in
 * module internals) and direct injection (for testability).</p>
 *
 * <p>Initialised once by {@code ZeldaBuilder.initialize(...)} — calling
 * {@link #get()} before that throws {@link IllegalStateException}.</p>
 */
public final class ZeldaContext {

    private static ZeldaContext instance;

    private final ZeldaPlugin    plugin;
    private final ModuleRegistry moduleRegistry;
    private volatile Object cachedInjector = null;
    private volatile java.lang.reflect.Method cachedInjectorMethod = null;


    // -----------------------------------------------------------------------
    // Construction — package-private, only ZeldaBuilder creates this
    // -----------------------------------------------------------------------

    ZeldaContext(ZeldaPlugin plugin, ModuleRegistry moduleRegistry) {
        if (plugin == null) throw new IllegalArgumentException("plugin must not be null");
        this.plugin         = plugin;
        this.moduleRegistry = moduleRegistry;
    }

    /**
     * Registers this context as the process-wide singleton.
     * Called exactly once by the builder.
     *
     * @throws IllegalStateException if called more than once
     */
    public static synchronized void init(ZeldaPlugin plugin, ModuleRegistry registry) {
        if (instance != null) {
            throw new IllegalStateException(
                    "ZeldaContext is already initialised. Call initialize() only once in onEnable()."
            );
        }
        instance = new ZeldaContext(plugin, registry);
    }

    /**
     * Tears down the singleton. Called by {@code Zelda.shutdown()} from
     * the platform adapter's {@code onDisable()} / shutdown hook.
     */
    public static synchronized void reset() {
        instance = null;
    }

    /**
     * Returns the process-wide {@link ZeldaContext}.
     *
     * @throws IllegalStateException if not yet initialised
     */
    public static ZeldaContext get() {
        if (instance == null) {
            throw new IllegalStateException(
                    "ZeldaContext has not been initialised. " +
                            "Did you call Zelda.builder().initialize(plugin)?"
            );
        }
        return instance;
    }

    /** The platform-agnostic plugin wrapper. */
    public ZeldaPlugin getPlugin() { return plugin; }

    /** Shortcut for {@code getPlugin().getLogger()}. */
    public Logger getLogger() { return plugin.getLogger(); }

    /** Shortcut for {@code getPlugin().getDataFolder()}. */
    public Path getDataFolder() { return plugin.getDataFolder(); }

    /** The module registry — look up any registered module by type. */
    public ModuleRegistry getRegistry() { return moduleRegistry; }

    public Object getInjector() {
        if (cachedInjector != null) return cachedInjector;

        synchronized (this) {
            if (cachedInjector != null) return cachedInjector;

            ZeldaModule injectionModule = moduleRegistry.getAll().get("injection");
            if (injectionModule == null) {
                throw new IllegalStateException(
                        "InjectionModule is not registered. Did you call .withInjection() on the builder?"
                );
            }

            try {
                if (cachedInjectorMethod == null) {
                    cachedInjectorMethod = injectionModule.getClass().getMethod("getInjector");
                    cachedInjectorMethod.setAccessible(true);
                }
                cachedInjector = cachedInjectorMethod.invoke(injectionModule);
                return cachedInjector;
            } catch (Exception e) {
                throw new IllegalStateException("Failed to get injector from InjectionModule", e);
            }
        }
    }


}