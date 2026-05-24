package net.kgomc.zelda.core.lifecycle;

/**
 * Optional lifecycle hooks a module can implement on top of {@link net.kgomc.zelda.core.module.ZeldaModule}.
 *
 * <p>Modules that need fine-grained control (e.g. running tasks after all
 * modules are enabled, or flushing state before the plugin unloads) can
 * implement this interface. The builder will detect and call these
 * automatically.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * public class DatabaseModule implements ZeldaModule, LifecycleHook {
 *
 *     @Override public void afterAllEnabled() {
 *         // safe to assume messaging module is also up here
 *     }
 *
 *     @Override public void beforeDisable() {
 *         // flush write-behind cache before connections close
 *     }
 * }
 * }</pre>
 */
public interface LifecycleHook {

    /**
     * Called after <em>all</em> registered modules have had their
     * {@code onEnable()} invoked. Safe to interact with sibling modules here.
     */
    default void afterAllEnabled() {}

    /**
     * Called just before any module's {@code onDisable()} is invoked.
     * Use for pre-shutdown cleanup that must happen before connections close.
     */
    default void beforeDisable() {}
}