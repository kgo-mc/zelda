package net.kgomc.zelda.messaging.palette;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the global {@link Palette} and all named theme palettes.
 *
 * <p>Theme palettes are pre-merged with the global palette at load time
 * so lookups are O(1) with no merging cost per message.</p>
 */
public final class PaletteRegistry {

    private volatile Palette globalPalette;

    /** Pre-merged palettes: theme name → merged Palette (global + theme overrides) */
    private final ConcurrentHashMap<String, Palette> themes = new ConcurrentHashMap<>();

    public PaletteRegistry(PaletteConfig config) {
        load(config);
    }

    // -----------------------------------------------------------------------
    // Load / reload
    // -----------------------------------------------------------------------

    /**
     * Loads palettes from config. Safe to call on reload — atomically replaces
     * the global palette and rebuilds all theme palettes.
     */
    public synchronized void load(PaletteConfig config) {
        Palette global = new Palette("global", config.global, config.gradients);
        this.globalPalette = global;

        themes.clear();
        for (Map.Entry<String, Map<String, String>> entry : config.themes.entrySet()) {
            String themeName = entry.getKey();
            Map<String, GradientEntry> gradientOverrides =
                    config.themeGradients.getOrDefault(themeName, Map.of());
            themes.put(themeName, global.merge(themeName, entry.getValue(), gradientOverrides));
        }
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    /** Returns the global palette. Never null. */
    public Palette getGlobal() {
        return globalPalette;
    }

    /**
     * Returns a named theme palette (pre-merged with global).
     * Falls back to global if the theme name is unknown.
     */
    public Palette getTheme(String themeName) {
        return themes.getOrDefault(themeName, globalPalette);
    }

    public Optional<Palette> findTheme(String themeName) {
        return Optional.ofNullable(themes.get(themeName));
    }

    /** Registers a programmatic theme with color overrides at runtime. */
    public void registerTheme(String name, Map<String, String> colorOverrides) {
        themes.put(name, globalPalette.merge(name, colorOverrides, Map.of()));
    }

    /** Registers a programmatic theme with both color and gradient overrides at runtime. */
    public void registerTheme(String name, Map<String, String> colorOverrides,
                              Map<String, GradientEntry> gradientOverrides) {
        themes.put(name, globalPalette.merge(name, colorOverrides, gradientOverrides));
    }
}