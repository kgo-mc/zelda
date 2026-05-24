package net.kgomc.zelda.ui.placeholder;

import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * Resolves a placeholder key to a string value for a given player.
 *
 * <p>Register a custom resolver on {@link PlaceholderService} to provide
 * values beyond what PAPI exposes.</p>
 *
 * <pre>{@code
 * placeholderService.addResolver((player, key) -> {
 *     if (key.equals("my_custom_stat")) {
 *         return Optional.of(String.valueOf(stats.get(player)));
 *     }
 *     return Optional.empty();
 * });
 * }</pre>
 */
@FunctionalInterface
public interface PlaceholderResolver {

    /**
     * @param player the viewing player
     * @param key    the placeholder key (without curly braces)
     * @return the resolved value, or empty to defer to the next resolver
     */
    Optional<String> resolve(Player player, String key);
}