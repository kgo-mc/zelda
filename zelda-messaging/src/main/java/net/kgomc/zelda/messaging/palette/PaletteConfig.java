package net.kgomc.zelda.messaging.palette;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * YAML model for {@code palette.yml}.
 *
 * <p>Example file:</p>
 * <pre>{@code
 * global:
 *   primary:   "#5865F2"
 *   secondary: "#EB459E"
 *   accent:    "#FEE75C"
 *   success:   "#57F287"
 *   error:     "#ED4245"
 *   warning:   "#FEE75C"
 *   muted:     "#B0B0B0"
 *
 * themes:
 *   vip:
 *     primary: "#FFD700"
 *     accent:  "#FFA500"
 *   admin:
 *     primary: "#FF4444"
 *     accent:  "#FF8888"
 * }</pre>
 *
 * <p>Theme entries only need to define the colors they override —
 * the rest are inherited from global.</p>
 */
@Configuration
public class PaletteConfig {

    @Comment("Global palette — base colors used when no per-player theme is active.")
    public Map<String, String> global = defaultGlobal();

    @Comment({
            "Named gradients — resolved as MiniMessage <gradient:...> tags.",
            "Each entry needs at least two hex color stops.",
            "Usage in messages: <rainbow>Hello!</rainbow>"
    })
    public Map<String, GradientEntry> gradients = defaultGradients();

    @Comment({
            "Named themes — each entry overrides specific global colors.",
            "Only define the colors you want to change; the rest inherit from global.",
            "Themes can also override gradients."
    })
    public Map<String, Map<String, String>> themes = new HashMap<>();

    /** Per-theme gradient overrides: theme name → (gradient name → GradientEntry) */
    public Map<String, Map<String, GradientEntry>> themeGradients = new HashMap<>();

    // -----------------------------------------------------------------------
    // Defaults
    // -----------------------------------------------------------------------

    private static Map<String, String> defaultGlobal() {
        Map<String, String> defaults = new HashMap<>();
        defaults.put("primary",   "#5865F2");
        defaults.put("secondary", "#EB459E");
        defaults.put("accent",    "#FEE75C");
        defaults.put("success",   "#57F287");
        defaults.put("error",     "#ED4245");
        defaults.put("warning",   "#FEE75C");
        defaults.put("muted",     "#B0B0B0");
        return defaults;
    }

    private static Map<String, GradientEntry> defaultGradients() {
        Map<String, GradientEntry> defaults = new HashMap<>();
        defaults.put("rainbow", new GradientEntry(List.of("#5865F2", "#EB459E")));
        return defaults;
    }
}