package net.kgomc.zelda.configuration.module;

import net.kgomc.zelda.configuration.registry.ConfigurationRegistry;
import net.kgomc.zelda.core.context.ZeldaContext;
import net.kgomc.zelda.core.module.ZeldaModule;

import java.nio.file.Path;

/**
 * Zelda configuration module.
 *
 * <p>Manages a {@link ConfigurationRegistry} rooted at a configurable base
 * directory. Register your config classes after the module is enabled, then
 * retrieve live instances anywhere via {@link #getRegistry()}.</p>
 *
 * <h2>Setup (in your plugin's onEnable)</h2>
 * <pre>{@code
 * Zelda.builder()
 *     .withConfiguration(getDataFolder().toPath().resolve("config"))
 *     .initialize(this);
 *
 * ConfigurationModule cfg = ZeldaContext.get()
 *     .getRegistry().find(ConfigurationModule.class).orElseThrow();
 *
 * cfg.getRegistry().register(SettingsConfig.class);
 * cfg.getRegistry().register(MessagesConfig.class);
 * }</pre>
 *
 * <h2>Retrieval anywhere in your code</h2>
 * <pre>{@code
 * SettingsConfig settings = cfg.getRegistry().get(SettingsConfig.class);
 * int maxPlayers = settings.maxPlayers;
 * }</pre>
 *
 * <h2>Hot-reload (e.g. from a /reload command)</h2>
 * <pre>{@code
 * cfg.getRegistry().reloadAll();
 * }</pre>
 */
public final class ConfigurationModule implements ZeldaModule {

    private final Path baseDir;
    private ConfigurationRegistry registry;

    public ConfigurationModule(Path baseDir) {
        this.baseDir = baseDir;
    }

    // -----------------------------------------------------------------------
    // ZeldaModule
    // -----------------------------------------------------------------------

    @Override
    public String getName() {
        return "configuration";
    }

    @Override
    public void onEnable(ZeldaContext context) {
        context.getLogger().info("[Zelda/Config] Initialising config registry at: " + baseDir);
        this.registry = new ConfigurationRegistry(baseDir, context.getLogger());
        context.getLogger().info("[Zelda/Config] Ready — register your configs via getRegistry().register(...)");
    }

    @Override
    public void onDisable() {
        // No persistent resources to clean up — YAML files are written on update/reload
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Returns the live {@link ConfigurationRegistry}.
     *
     * @throws IllegalStateException if called before the module is enabled
     */
    public ConfigurationRegistry getRegistry() {
        if (registry == null) {
            throw new IllegalStateException("ConfigurationModule is not yet enabled.");
        }
        return registry;
    }
}