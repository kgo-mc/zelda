package net.kgomc.zelda.messaging.formatter;

import net.kgomc.zelda.messaging.palette.Palette;
import net.kgomc.zelda.messaging.palette.PaletteRegistry;
import net.kgomc.zelda.messaging.palette.ThemeProvider;
import net.kgomc.zelda.messaging.target.ITarget;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.Map;
import java.util.UUID;

/**
 * Formats raw MiniMessage strings into {@link Component}s using the active
 * palette for the target player.
 *
 * <h2>Pipeline</h2>
 * <ol>
 *   <li>Resolve placeholders — substitute {@code {key}} tokens from the provided map</li>
 *   <li>Resolve active palette — global, or per-player theme via {@link ThemeProvider}</li>
 *   <li>Parse with MiniMessage + palette {@link TagResolver} → {@link Component}</li>
 * </ol>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * Component c = formatter.format(target, "<primary>Hello <accent>{name}</accent>!",
 *     Map.of("name", "Steve"));
 * }</pre>
 */
public final class MessageFormatter {

    private final MiniMessage      miniMessage;
    private final PaletteRegistry  paletteRegistry;
    private final ThemeProvider    themeProvider;

    public MessageFormatter(PaletteRegistry paletteRegistry, ThemeProvider themeProvider) {
        this.paletteRegistry = paletteRegistry;
        this.themeProvider   = themeProvider;
        // Standard MiniMessage — palette tags added per-call via TagResolver
        this.miniMessage     = MiniMessage.miniMessage();
    }

    // -----------------------------------------------------------------------
    // Format
    // -----------------------------------------------------------------------

    /**
     * Formats a raw string for the given target.
     * Placeholder substitution then palette tag resolution.
     *
     * @param target      the recipient — used to resolve the active theme
     * @param raw         raw MiniMessage string with optional {placeholders}
     * @param placeholders key→value map for {placeholder} substitution
     * @return formatted {@link Component}
     */
    public Component format(ITarget target, String raw, Map<String, String> placeholders) {
        String substituted = substitutePlaceholders(raw, placeholders);
        Palette palette    = resolvePalette(target);
        return miniMessage.deserialize(substituted, palette.getTagResolver());
    }

    /**
     * Formats a raw string for the given target with no placeholders.
     */
    public Component format(ITarget target, String raw) {
        return format(target, raw, Map.of());
    }

    /**
     * Formats a raw string using the global palette (no target needed).
     * Useful for console messages or broadcast formatting.
     */
    public Component formatGlobal(String raw, Map<String, String> placeholders) {
        String substituted = substitutePlaceholders(raw, placeholders);
        Palette palette    = paletteRegistry.getGlobal();
        return miniMessage.deserialize(substituted, palette.getTagResolver());
    }

    public Component formatGlobal(String raw) {
        return formatGlobal(raw, Map.of());
    }

    /**
     * Formats using a specific named theme — bypasses ThemeProvider.
     * Useful when you know the theme explicitly (e.g. previewing a theme).
     */
    public Component formatWithTheme(String themeName, String raw, Map<String, String> placeholders) {
        String substituted = substitutePlaceholders(raw, placeholders);
        Palette palette    = paletteRegistry.getTheme(themeName);
        return miniMessage.deserialize(substituted, palette.getTagResolver());
    }

    // -----------------------------------------------------------------------
    // Additional resolvers — Nexo / custom tag support
    // -----------------------------------------------------------------------

    /**
     * Formats with the palette resolver PLUS additional custom {@link TagResolver}s.
     * Use this to layer Nexo custom item tags or any other MiniMessage extension
     * on top of the palette.
     *
     * <pre>{@code
     * Component c = formatter.formatWithResolvers(
     *     target, "<primary><nexo:sword_icon> Buy</primary>",
     *     Map.of(),
     *     nexoTagResolver
     * );
     * }</pre>
     */
    public Component formatWithResolvers(ITarget target, String raw,
                                         Map<String, String> placeholders,
                                         TagResolver... extraResolvers) {
        String substituted = substitutePlaceholders(raw, placeholders);
        Palette palette    = resolvePalette(target);

        TagResolver combined = TagResolver.resolver(palette.getTagResolver(),
                TagResolver.resolver(extraResolvers));

        return miniMessage.deserialize(substituted, combined);
    }

    // -----------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------

    /**
     * Substitutes {@code {key}} tokens from the placeholder map.
     * Unknown tokens are left unchanged.
     */
    private static String substitutePlaceholders(String raw, Map<String, String> placeholders) {
        if (raw == null || raw.isEmpty() || placeholders.isEmpty()) return raw;
        String result = raw;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    /**
     * Resolves the active {@link Palette} for a target.
     * Player targets go through {@link ThemeProvider}; non-players use global.
     */
    private Palette resolvePalette(ITarget target) {
        if (!target.isPlayer()) return paletteRegistry.getGlobal();
        UUID uuid = target.getUUID().orElse(null);
        if (uuid == null) return paletteRegistry.getGlobal();
        String themeName = themeProvider.getTheme(uuid);
        return paletteRegistry.getTheme(themeName);
    }
}