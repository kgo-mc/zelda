package net.kgomc.zelda.builder;

import net.kgomc.zelda.core.context.ZeldaContext;
import net.kgomc.zelda.core.module.ModuleRegistry;

/**
 * Static entry point for the Zelda library.
 *
 * <h2>Starting Zelda</h2>
 * <pre>{@code
 * // Paper
 * Zelda.builder()
 *     .centralConfig(Path.of("plugins/Zelda"))
 *     .withDatabase()
 *     .withConfiguration()
 *     .withMessaging()
 *     .withUI()
 *     .initialize(new PaperPluginAdapter(this));
 *
 * // Velocity
 * Zelda.builder()
 *     .centralConfig(Path.of("plugins/Zelda"))
 *     .withDatabase()
 *     .withMessaging()
 *     .initialize(new VelocityPluginAdapter(container, logger, dataDir, server));
 * }</pre>
 *
 * <h2>Shutting down</h2>
 * <pre>{@code
 * // In onDisable() / ProxyShutdownEvent
 * Zelda.shutdown();
 * }</pre>
 *
 * <h2>Accessing modules after init</h2>
 * <pre>{@code
 * // From anywhere in your plugin
 * DatabaseModule db = ZeldaContext.get()
 *     .getRegistry()
 *     .find(DatabaseModule.class)
 *     .orElseThrow();
 * }</pre>
 */
public final class Zelda {

    private Zelda() {}

    /**
     * Returns a new {@link ZeldaBuilder} to configure and initialise the library.
     */
    public static ZeldaBuilder builder() {
        return new ZeldaBuilder();
    }

    /**
     * Disables all registered modules in reverse order and resets the context.
     * Call from your plugin's {@code onDisable()} or Velocity's shutdown event.
     *
     * <p>Safe to call even if Zelda was never initialised.</p>
     */
    public static void shutdown() {
        try {
            ZeldaContext ctx = ZeldaContext.get();
            ctx.getRegistry().disableAll();
        } catch (IllegalStateException ignored) {
        } finally {
            ZeldaContext.reset();
            net.kgomc.zelda.core.serialization.ZeldaGson.reset();
        }
    }

    /**
     * Returns true if Zelda has been initialised and not yet shut down.
     */
    public static boolean isInitialised() {
        try {
            ZeldaContext.get();
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /**
     * Returns the active {@link ModuleRegistry} — shortcut for
     * {@code ZeldaContext.get().getRegistry()}.
     *
     * @throws IllegalStateException if Zelda is not initialised
     */
    public static ModuleRegistry modules() {
        return ZeldaContext.get().getRegistry();
    }
}