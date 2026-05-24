package net.kgomc.zelda.core.module;

import net.kgomc.zelda.core.context.ZeldaContext;

/**
 * Contract that every Zelda module must fulfil.
 *
 * <p>The builder calls {@link #onEnable(ZeldaContext)} after the context is
 * ready, and {@link #onDisable()} when the plugin shuts down.</p>
 *
 * <p>Implement this interface in each module's main entry class, e.g.
 * {@code DatabaseModule}, {@code MessagingModule}.</p>
 */
public interface ZeldaModule {

    /**
     * Unique, human-readable name for this module (used in logs).
     * Example: {@code "database"}, {@code "messaging"}.
     */
    String getName();

    /**
     * Called once after {@link ZeldaContext} is initialised.
     * Set up connections, register listeners, etc. here.
     *
     * @param context the fully initialised Zelda context
     */
    void onEnable(ZeldaContext context);

    /**
     * Called when the host plugin is disabling.
     * Close connections, flush caches, unregister listeners, etc.
     */
    void onDisable();
}