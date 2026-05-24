package net.kgomc.zelda.builder;

import net.kgomc.zelda.core.context.ZeldaContext;
import net.kgomc.zelda.core.context.ZeldaPlugin;
import net.kgomc.zelda.core.module.ModuleRegistry;

import java.nio.file.Path;

/**
 * Fluent builder for the Zelda library.
 *
 * <p>Obtain via {@link Zelda#builder()}, chain the modules you need, then
 * call {@link #initialize(ZeldaPlugin)} to boot everything.</p>
 *
 * <h2>Typical Paper usage</h2>
 * <pre>{@code
 * public class MyPlugin extends JavaPlugin {
 *
 *     @Override
 *     public void onEnable() {
 *         Zelda.builder()
 *             .centralConfig(Path.of("plugins/Zelda"))
 *             .withDatabase()
 *             .withConfiguration()
 *             .withMessaging()
 *             .withUI()
 *             .initialize(new PaperPluginAdapter(this));
 *     }
 *
 *     @Override
 *     public void onDisable() {
 *         Zelda.shutdown();
 *     }
 * }
 * }</pre>
 *
 * <h2>Path resolution</h2>
 * <ul>
 *   <li>If {@link #centralConfig(Path)} is set, shared files (palette.yml, database.json,
 *       messages.yml) are resolved from there.</li>
 *   <li>If not set, they fall back to the plugin's data folder.</li>
 *   <li>Explicit paths passed to individual {@code with*()} methods always win.</li>
 * </ul>
 */
public final class ZeldaBuilder {

    // Central shared config directory — optional
    private Path centralConfigPath = null;

    // Per-module paths — null means "use resolved default"
    private Path databaseConfigPath      = null;
    private Path pluginConfigPath        = null;
    private Path paletteConfigPath       = null;
    private Path messagesConfigPath      = null;

    // Which modules to enable
    private boolean withDatabase      = false;
    private boolean withConfiguration = false;
    private boolean withMessaging     = false;
    private boolean withUI            = false;
    private boolean withInjection     = false;
    private boolean withOutbox        = false;
    private int     outboxPollSeconds = 10;
    private int     outboxBatchSize   = 50;
    private int     outboxMaxAttempts = 3;

    // User binder — populated by withInjection()
    private Object userBinder = null;

    ZeldaBuilder() {}

    // -----------------------------------------------------------------------
    // Central config
    // -----------------------------------------------------------------------

    /**
     * Sets the central shared config directory used by all Zelda plugins on this server.
     * If not set, each module falls back to the plugin's own data folder.
     *
     * <pre>{@code
     * .centralConfig(Path.of("plugins/Zelda"))
     * }</pre>
     */
    public ZeldaBuilder centralConfig(Path path) {
        this.centralConfigPath = path;
        return this;
    }

    // -----------------------------------------------------------------------
    // Module registration
    // -----------------------------------------------------------------------

    /**
     * Enables the database module.
     * Reads {@code database.json} from the central config dir or plugin data folder.
     */
    public ZeldaBuilder withDatabase() {
        this.withDatabase = true;
        return this;
    }

    /**
     * Enables the database module with an explicit config path.
     */
    public ZeldaBuilder withDatabase(Path configPath) {
        this.withDatabase = true;
        this.databaseConfigPath = configPath;
        return this;
    }

    /**
     * Enables the configuration module.
     * Uses the plugin's data folder as the base directory for YAML configs.
     */
    public ZeldaBuilder withConfiguration() {
        this.withConfiguration = true;
        return this;
    }

    /**
     * Enables the configuration module with an explicit base directory.
     */
    public ZeldaBuilder withConfiguration(Path basePath) {
        this.withConfiguration = true;
        this.pluginConfigPath = basePath;
        return this;
    }

    /**
     * Enables the messaging module.
     * Reads {@code palette.yml} and {@code messages.yml} from the central config
     * dir (if set) or the plugin data folder.
     */
    public ZeldaBuilder withMessaging() {
        this.withMessaging = true;
        return this;
    }

    /**
     * Enables the messaging module with explicit paths.
     */
    public ZeldaBuilder withMessaging(Path paletteConfigPath, Path messagesConfigPath) {
        this.withMessaging = true;
        this.paletteConfigPath  = paletteConfigPath;
        this.messagesConfigPath = messagesConfigPath;
        return this;
    }

    /**
     * Enables the UI module (server-only — guarded by RuntimeKind.SERVER).
     */
    public ZeldaBuilder withUI() {
        this.withUI = true;
        return this;
    }

    /** Enables the outbox module with defaults (5s poll, batch 50, 3 max attempts). */
    public ZeldaBuilder withOutbox() {
        this.withOutbox = true;
        return this;
    }

    /** Enables the outbox module with custom settings. */
    public ZeldaBuilder withOutbox(int pollIntervalSeconds, int batchSize, int maxAttempts) {
        this.withOutbox        = true;
        this.outboxPollSeconds = pollIntervalSeconds;
        this.outboxBatchSize   = batchSize;
        this.outboxMaxAttempts = maxAttempts;
        return this;
    }

    /**
     * Enables the injection module with no user bindings.
     * Zelda modules and internals are still auto-bound.
     */
    public ZeldaBuilder withInjection() {
        this.withInjection = true;
        return this;
    }

    /**
     * Enables the injection module with user-declared bindings.
     *
     * <pre>{@code
     * .withInjection(binder -> binder
     *     .bind(EconomyService.class, EconomyServiceImpl.class)
     *     .bindInstance(Config.class, myConfig)
     * )
     * }</pre>
     */
    public ZeldaBuilder withInjection(java.util.function.Consumer<Object> binderConsumer) {
        this.withInjection = true;
        try {
            Class<?> binderClass = Class.forName("net.kgomc.zelda.injection.binder.ZeldaBinder");
            Object binder = binderClass.getDeclaredConstructor().newInstance();
            binderConsumer.accept(binder);
            this.userBinder = binder;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "[Zelda] zelda-injection is not on the classpath. " +
                            "Add it as a dependency in your plugin's pom.xml.", e);
        }
        return this;
    }

    // -----------------------------------------------------------------------
    // Initialize
    // -----------------------------------------------------------------------

    /**
     * Builds the {@link ZeldaContext}, registers all chosen modules into the
     * {@link ModuleRegistry}, and calls {@code onEnable()} on each in order.
     *
     * @param plugin the platform adapter for this plugin
     * @throws IllegalStateException if {@link ZeldaContext} is already initialised
     */
    public void initialize(ZeldaPlugin plugin) {
        Path dataFolder = plugin.getDataFolder();
        Path baseConfig = centralConfigPath != null ? centralConfigPath : dataFolder;

        ModuleRegistry registry = new ModuleRegistry(plugin.getLogger());

        // Register modules in dependency order
        if (withDatabase) {
            Path path = databaseConfigPath != null
                    ? databaseConfigPath
                    : baseConfig.resolve("database.json");
            registry.register(createDatabaseModule(path));
        }

        if (withConfiguration) {
            Path path = pluginConfigPath != null
                    ? pluginConfigPath
                    : dataFolder; // config always plugin-local unless explicitly overridden
            registry.register(createConfigurationModule(path));
        }

        if (withMessaging) {
            Path palette  = paletteConfigPath  != null ? paletteConfigPath  : baseConfig.resolve("palette.yml");
            Path messages = messagesConfigPath != null ? messagesConfigPath : baseConfig.resolve("messages.yml");
            registry.register(createMessagingModule(palette, messages));
        }

        if (withUI) {
            registry.register(createUIModule());
        }

        if (withOutbox) {
            registry.register(createOutboxModule(outboxPollSeconds, outboxBatchSize, outboxMaxAttempts));
        }

        // Injection must be registered LAST — it fires afterAllEnabled() to auto-bind everything
        if (withInjection) {
            registry.register(createInjectionModule(userBinder));
        }

        // Boot — init context then enable all modules
        ZeldaContext.init(plugin, registry);
        registry.enableAll(ZeldaContext.get());

        plugin.getLogger().info("[Zelda] Initialised with "
                + registry.getAll().size() + " module(s): "
                + String.join(", ", registry.getAll().keySet()));
    }

    // -----------------------------------------------------------------------
    // Module factory methods — reflective to keep optional deps truly optional
    // -----------------------------------------------------------------------

    private static net.kgomc.zelda.core.module.ZeldaModule createDatabaseModule(Path path) {
        try {
            return (net.kgomc.zelda.core.module.ZeldaModule)
                    Class.forName("net.kgomc.zelda.database.module.DatabaseModule")
                            .getConstructor(Path.class)
                            .newInstance(path);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "[Zelda] zelda-database is not on the classpath. " +
                            "Add it as a dependency in your plugin's pom.xml.", e);
        }
    }

    private static net.kgomc.zelda.core.module.ZeldaModule createConfigurationModule(Path path) {
        try {
            return (net.kgomc.zelda.core.module.ZeldaModule)
                    Class.forName("net.kgomc.zelda.configuration.module.ConfigurationModule")
                            .getConstructor(Path.class)
                            .newInstance(path);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "[Zelda] zelda-configuration is not on the classpath. " +
                            "Add it as a dependency in your plugin's pom.xml.", e);
        }
    }

    private static net.kgomc.zelda.core.module.ZeldaModule createMessagingModule(Path palette, Path messages) {
        try {
            return (net.kgomc.zelda.core.module.ZeldaModule)
                    Class.forName("net.kgomc.zelda.messaging.module.MessagingModule")
                            .getConstructor(Path.class, Path.class)
                            .newInstance(palette, messages);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "[Zelda] zelda-messaging is not on the classpath. " +
                            "Add it as a dependency in your plugin's pom.xml.", e);
        }
    }

    private static net.kgomc.zelda.core.module.ZeldaModule createOutboxModule(
            int pollSeconds, int batchSize, int maxAttempts) {
        try {
            return (net.kgomc.zelda.core.module.ZeldaModule)
                    Class.forName("net.kgomc.zelda.outbox.module.OutboxModule")
                            .getConstructor(int.class, int.class, int.class)
                            .newInstance(pollSeconds, batchSize, maxAttempts);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "[Zelda] zelda-outbox is not on the classpath. " +
                            "Add it as a dependency in your plugin's pom.xml.", e);
        }
    }

    private static net.kgomc.zelda.core.module.ZeldaModule createInjectionModule(Object binder) {
        try {
            Class<?> moduleClass = Class.forName("net.kgomc.zelda.injection.module.InjectionModule");
            Class<?> binderClass = Class.forName("net.kgomc.zelda.injection.binder.ZeldaBinder");
            return (net.kgomc.zelda.core.module.ZeldaModule)
                    moduleClass.getConstructor(binderClass).newInstance(binder);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "[Zelda] zelda-injection is not on the classpath. " +
                            "Add it as a dependency in your plugin's pom.xml.", e);
        }
    }

    private static net.kgomc.zelda.core.module.ZeldaModule createUIModule() {
        try {
            return (net.kgomc.zelda.core.module.ZeldaModule)
                    Class.forName("net.kgomc.zelda.ui.module.UIModule")
                            .getDeclaredConstructor()
                            .newInstance();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "[Zelda] zelda-ui is not on the classpath. " +
                            "Add it as a dependency in your plugin's pom.xml.", e);
        }
    }
}