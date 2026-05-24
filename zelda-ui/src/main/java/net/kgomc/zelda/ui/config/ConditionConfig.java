package net.kgomc.zelda.ui.config;

import de.exlll.configlib.Configuration;

import java.util.Map;

/**
 * A single condition entry inside {@code showIf} or {@code showIfFirst}.
 *
 * <p>The {@link #showIf} string must match a method annotated with
 * {@code @ShowIf("name")} on the menu's handler class. If omitted, this
 * entry acts as an unconditional default (useful as the last entry in
 * {@code showIfFirst} to guarantee a fallback).</p>
 *
 * <p>Example YAML:</p>
 * <pre>{@code
 * showIfFirst:
 *   - showIf: isVip
 *     overrides:
 *       material: DIAMOND
 *       name: "<gold>VIP Shop"
 *   - showIf: hasCoins
 *     overrides:
 *       material: GOLD_INGOT
 *   - overrides:           # no showIf = always matches (default fallback)
 *       material: STONE
 * }</pre>
 */
@Configuration
public class ConditionConfig {

    /**
     * Name of the handler method annotated with {@code @ShowIf("name")}.
     * If null or blank, this condition is treated as always-true.
     */
    private String showIf = null;

    /**
     * Property overrides applied when this condition passes.
     * Keys match field names of {@link ItemConfig} (material, name, lore,
     * count, enchanted, customModelData).
     */
    private Map<String, Object> overrides = Map.of();

    public String showIf() {
        return showIf;
    }

    public Map<String, Object> overrides() {
        return overrides;
    }
}