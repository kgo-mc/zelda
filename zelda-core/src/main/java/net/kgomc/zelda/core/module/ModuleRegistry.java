package net.kgomc.zelda.core.module;

import net.kgomc.zelda.core.context.ZeldaContext;
import net.kgomc.zelda.core.lifecycle.LifecycleHook;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tracks all registered {@link ZeldaModule}s and drives their lifecycle.
 *
 * <p>Modules are stored in insertion order so that enable/disable sequences
 * are deterministic and predictable.</p>
 */
public final class ModuleRegistry {

    private final Map<String, ZeldaModule> modules = new LinkedHashMap<>();
    private final Logger logger;

    public ModuleRegistry(Logger logger) {
        this.logger = logger;
    }

    // -----------------------------------------------------------------------
    // Registration
    // -----------------------------------------------------------------------

    /**
     * Registers a module. Duplicate names are rejected.
     *
     * @throws IllegalArgumentException if a module with the same name is already registered
     */
    public void register(ZeldaModule module) {
        String name = module.getName();
        if (modules.containsKey(name)) {
            throw new IllegalArgumentException(
                    "A module named '" + name + "' is already registered."
            );
        }
        modules.put(name, module);
        logger.info("[Zelda] Registered module: " + name);
    }

    // -----------------------------------------------------------------------
    // Lifecycle — called by ZeldaBuilder
    // -----------------------------------------------------------------------

    /** Enables all registered modules in insertion order. */
    public void enableAll(ZeldaContext context) {
        for (ZeldaModule module : modules.values()) {
            try {
                logger.info("[Zelda] Enabling module: " + module.getName());
                module.onEnable(context);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "[Zelda] Failed to enable module: " + module.getName(), e);
            }
        }

        // Fire afterAllEnabled for modules that implement LifecycleHook
        for (ZeldaModule module : modules.values()) {
            if (module instanceof LifecycleHook hook) {
                try {
                    hook.afterAllEnabled();
                } catch (Exception e) {
                    logger.log(Level.SEVERE,
                            "[Zelda] afterAllEnabled() failed for module: " + module.getName(), e);
                }
            }
        }
    }

    /** Fires beforeDisable hooks, then disables all modules in reverse order. */
    public void disableAll() {
        // beforeDisable hooks first
        for (ZeldaModule module : modules.values()) {
            if (module instanceof LifecycleHook hook) {
                try {
                    hook.beforeDisable();
                } catch (Exception e) {
                    logger.log(Level.SEVERE,
                            "[Zelda] beforeDisable() failed for module: " + module.getName(), e);
                }
            }
        }

        // Disable in reverse insertion order
        var reversed = new java.util.ArrayList<>(modules.values());
        Collections.reverse(reversed);
        for (ZeldaModule module : reversed) {
            try {
                logger.info("[Zelda] Disabling module: " + module.getName());
                module.onDisable();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "[Zelda] Failed to disable module: " + module.getName(), e);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Lookup
    // -----------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public <T extends ZeldaModule> Optional<T> find(Class<T> type) {
        return modules.values().stream()
                .filter(type::isInstance)
                .map(m -> (T) m)
                .findFirst();
    }

    public Map<String, ZeldaModule> getAll() {
        return Collections.unmodifiableMap(modules);
    }
}