package net.kgomc.zelda.messaging.module;

import net.kgomc.zelda.core.context.ZeldaContext;
import net.kgomc.zelda.core.module.ZeldaModule;
import net.kgomc.zelda.messaging.channel.*;
import net.kgomc.zelda.messaging.formatter.MessageFormatter;
import net.kgomc.zelda.messaging.palette.*;
import net.kgomc.zelda.messaging.target.ITarget;
import net.kgomc.zelda.messaging.template.MessagesConfig;
import net.kgomc.zelda.messaging.template.TemplateRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Zelda messaging module.
 *
 * <p>Provides palette-driven MiniMessage formatting, named message templates,
 * and multi-channel delivery to any {@link ITarget}.</p>
 *
 * <h2>Setup</h2>
 * <pre>{@code
 * Zelda.builder()
 *     .withMessaging()
 *     .initialize(new PaperPluginAdapter(this));
 *
 * MessagingModule messaging = ZeldaContext.get()
 *     .getRegistry().find(MessagingModule.class).orElseThrow();
 * }</pre>
 *
 * <h2>Send a raw message</h2>
 * <pre>{@code
 * messaging.send(PlayerTarget.of(player), "<primary>Hello!</primary>");
 * messaging.send(PlayerTarget.of(player), "<primary>Hello <accent>{name}</accent>!",
 *     Map.of("name", player.getName()));
 * }</pre>
 *
 * <h2>Send a named template</h2>
 * <pre>{@code
 * messaging.sendTemplate(target, "join", Map.of("player_name", player.getName()));
 * }</pre>
 *
 * <h2>Action bar, title, boss bar, sound</h2>
 * <pre>{@code
 * messaging.sendActionBar(target, "<accent>Coins: {coins}</accent>", Map.of("coins", "100"));
 * messaging.sendTitle(target, TitleData.of("<primary>Round Start!</primary>", "<muted>Get ready</muted>"));
 * messaging.sendSound(target, SoundData.of("entity.experience_orb.pickup"));
 * }</pre>
 *
 * <h2>Broadcast</h2>
 * <pre>{@code
 * messaging.broadcast("<rainbow>Server restarting in 5 minutes!</rainbow>");
 * }</pre>
 *
 * <h2>Register a custom ThemeProvider (from zelda-player)</h2>
 * <pre>{@code
 * messaging.setThemeProvider(uuid -> playerService.getTheme(uuid));
 * }</pre>
 */
public final class MessagingModule implements ZeldaModule {

    private final Path paletteConfigPath;
    private final Path messagesConfigPath;

    private PaletteRegistry  paletteRegistry;
    private TemplateRegistry templateRegistry;
    private MessageFormatter formatter;
    private ThemeProvider    themeProvider = ThemeProvider.GLOBAL;
    private Logger           logger;

    public MessagingModule(Path paletteConfigPath, Path messagesConfigPath) {
        this.paletteConfigPath  = paletteConfigPath;
        this.messagesConfigPath = messagesConfigPath;
    }

    // -----------------------------------------------------------------------
    // ZeldaModule
    // -----------------------------------------------------------------------

    @Override
    public String getName() { return "messaging"; }

    @Override
    public void onEnable(ZeldaContext context) {
        this.logger = context.getLogger();
        logger.info("[Zelda/Messaging] Initialising...");

        // Load palette
        PaletteConfig paletteConfig = loadPaletteConfig();
        this.paletteRegistry = new PaletteRegistry(paletteConfig);

        // Load message templates
        MessagesConfig messagesConfig = loadMessagesConfig();
        this.templateRegistry = new TemplateRegistry();
        this.templateRegistry.load(messagesConfig);

        // Wire formatter
        this.formatter = new MessageFormatter(paletteRegistry, themeProvider);

        logger.info("[Zelda/Messaging] Ready — "
                + paletteConfig.global.size() + " palette colors, "
                + paletteConfig.gradients.size() + " gradients, "
                + templateRegistry.keys().size() + " message templates.");
    }

    @Override
    public void onDisable() {
        // Nothing to close
    }

    // -----------------------------------------------------------------------
    // Theme provider
    // -----------------------------------------------------------------------

    /**
     * Sets the {@link ThemeProvider} used to resolve per-player themes.
     * Call this from {@code zelda-player} on startup — before any messages are sent.
     * Safe to call before or after {@code onEnable}.
     */
    public void setThemeProvider(ThemeProvider themeProvider) {
        this.themeProvider = themeProvider;
        // Re-wire formatter if already enabled
        if (formatter != null) {
            this.formatter = new MessageFormatter(paletteRegistry, themeProvider);
        }
    }

    // -----------------------------------------------------------------------
    // Send — raw strings
    // -----------------------------------------------------------------------

    /** Sends a formatted chat message to the target. */
    public void send(ITarget target, String raw) {
        target.sendMessage(formatter.format(target, raw));
    }

    /** Sends a formatted chat message with placeholder substitution. */
    public void send(ITarget target, String raw, Map<String, String> placeholders) {
        target.sendMessage(formatter.format(target, raw, placeholders));
    }

    /** Sends a formatted action bar message. */
    public void sendActionBar(ITarget target, String raw) {
        target.sendActionBar(formatter.format(target, raw));
    }

    public void sendActionBar(ITarget target, String raw, Map<String, String> placeholders) {
        target.sendActionBar(formatter.format(target, raw, placeholders));
    }

    /** Sends a title. Title and subtitle strings are formatted separately. */
    public void sendTitle(ITarget target, TitleData data) {
        Component title    = formatter.format(target, data.title());
        Component subtitle = data.subtitle() != null
                ? formatter.format(target, data.subtitle())
                : Component.empty();
        target.sendTitle(data, title, subtitle);
    }

    /** Shows a boss bar. */
    public void showBossBar(ITarget target, BossBarData data) {
        Component name = formatter.format(target, data.name());
        target.showBossBar(data, name);
    }

    /** Plays a sound. */
    public void playSound(ITarget target, SoundData data) {
        target.playSound(data);
    }

    // -----------------------------------------------------------------------
    // Send — named templates
    // -----------------------------------------------------------------------

    /**
     * Sends a named template to the target.
     *
     * @throws IllegalArgumentException if the template key is not registered
     */
    public void sendTemplate(ITarget target, String key) {
        send(target, templateRegistry.getOrThrow(key));
    }

    public void sendTemplate(ITarget target, String key, Map<String, String> placeholders) {
        send(target, templateRegistry.getOrThrow(key), placeholders);
    }

    public void sendTemplateActionBar(ITarget target, String key, Map<String, String> placeholders) {
        sendActionBar(target, templateRegistry.getOrThrow(key), placeholders);
    }

    // -----------------------------------------------------------------------
    // Broadcast
    // -----------------------------------------------------------------------

    /**
     * Broadcasts a raw message to all online players using the global palette.
     * Only works on Paper — no-op on Velocity (use per-player sends there).
     */
    public void broadcast(String raw) {
        broadcast(raw, Map.of());
    }

    public void broadcast(String raw, Map<String, String> placeholders) {
        try {
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
            Object server = bukkit.getMethod("getServer").invoke(null);
            Component message = formatter.formatGlobal(raw, placeholders);
            server.getClass().getMethod("broadcast", Component.class).invoke(server, message);
        } catch (Exception e) {
            logger.warning("[Zelda/Messaging] broadcast() is only supported on Paper.");
        }
    }

    // -----------------------------------------------------------------------
    // Format only (returns Component without sending)
    // -----------------------------------------------------------------------

    public Component format(ITarget target, String raw) {
        return formatter.format(target, raw);
    }

    public Component format(ITarget target, String raw, Map<String, String> placeholders) {
        return formatter.format(target, raw, placeholders);
    }

    public Component formatGlobal(String raw) {
        return formatter.formatGlobal(raw);
    }

    public Component formatWithResolvers(ITarget target, String raw,
                                         Map<String, String> placeholders,
                                         TagResolver... extra) {
        return formatter.formatWithResolvers(target, raw, placeholders, extra);
    }

    // -----------------------------------------------------------------------
    // Palette & template access
    // -----------------------------------------------------------------------

    public PaletteRegistry getPaletteRegistry()   { return paletteRegistry; }
    public TemplateRegistry getTemplateRegistry() { return templateRegistry; }
    public MessageFormatter getFormatter()        { return formatter; }

    // -----------------------------------------------------------------------
    // Reload
    // -----------------------------------------------------------------------

    /**
     * Reloads palette and message templates from disk.
     * Theme provider and programmatic template overrides are preserved.
     */
    public void reload() {
        logger.info("[Zelda/Messaging] Reloading...");
        paletteRegistry.load(loadPaletteConfig());
        templateRegistry.load(loadMessagesConfig());
        this.formatter = new MessageFormatter(paletteRegistry, themeProvider);
        logger.info("[Zelda/Messaging] Reload complete.");
    }

    // -----------------------------------------------------------------------
    // Config loading
    // -----------------------------------------------------------------------

    private PaletteConfig loadPaletteConfig() {
        try {
            de.exlll.configlib.YamlConfigurationProperties props =
                    de.exlll.configlib.YamlConfigurationProperties.newBuilder().build();
            de.exlll.configlib.YamlConfigurationStore<PaletteConfig> store =
                    new de.exlll.configlib.YamlConfigurationStore<>(PaletteConfig.class, props);
            return store.update(paletteConfigPath);
        } catch (Exception e) {
            logger.warning("[Zelda/Messaging] Could not load palette.yml — using defaults. " + e.getMessage());
            return new PaletteConfig();
        }
    }

    private MessagesConfig loadMessagesConfig() {
        try {
            de.exlll.configlib.YamlConfigurationProperties props =
                    de.exlll.configlib.YamlConfigurationProperties.newBuilder().build();
            de.exlll.configlib.YamlConfigurationStore<MessagesConfig> store =
                    new de.exlll.configlib.YamlConfigurationStore<>(MessagesConfig.class, props);
            return store.update(messagesConfigPath);
        } catch (Exception e) {
            logger.warning("[Zelda/Messaging] Could not load messages.yml — using defaults. " + e.getMessage());
            return new MessagesConfig();
        }
    }
}