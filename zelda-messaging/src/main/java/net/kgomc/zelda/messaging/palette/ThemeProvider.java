package net.kgomc.zelda.messaging.palette;

import java.util.UUID;

/**
 * Resolves the active palette theme name for a given player.
 *
 * <p>The default implementation always returns {@code "global"}.
 * Register a custom implementation to support per-player themes:</p>
 *
 * <pre>{@code
 * // In zelda-player, on login:
 * messaging.setThemeProvider(uuid -> playerService.getTheme(uuid));
 * }</pre>
 *
 * <p>This interface is the seam between {@code zelda-messaging} and
 * {@code zelda-player} — messaging never imports player module classes.</p>
 */
@FunctionalInterface
public interface ThemeProvider {

    /** Always returns global — used when no per-player theme system is configured. */
    ThemeProvider GLOBAL = uuid -> "global";

    /**
     * Returns the active theme name for the given player UUID.
     * Return {@code "global"} to use the default palette.
     *
     * @param playerUuid the player's UUID
     * @return theme name matching an entry in {@link PaletteConfig#themes}, or "global"
     */
    String getTheme(UUID playerUuid);
}