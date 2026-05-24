package net.kgomc.zelda.messaging.palette;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;

import java.util.List;

/**
 * A named gradient defined by two or more color stops.
 *
 * <p>Resolves as a MiniMessage {@code <gradient:...>} tag so it can be used
 * exactly like a solid color palette entry in message strings.</p>
 *
 * <p>Example YAML:</p>
 * <pre>{@code
 * gradients:
 *   rainbow:
 *     stops: ["#5865F2", "#EB459E"]
 *   fire:
 *     stops: ["#FF4500", "#FF8C00", "#FFD700"]
 *   ocean:
 *     stops: ["#006994", "#00BFFF", "#E0FFFF"]
 * }</pre>
 *
 * <p>Usage in messages:</p>
 * <pre>{@code
 * <rainbow>Welcome to KGO MC!</rainbow>
 * <fire>Server restarting!</fire>
 * }</pre>
 */
@Configuration
public class GradientEntry {

    @Comment({
            "Two or more hex color stops for the gradient.",
            "MiniMessage applies them left-to-right across the text."
    })
    private List<String> stops = List.of("#FFFFFF", "#AAAAAA");

    public GradientEntry() {}

    public GradientEntry(List<String> stops) {
        this.stops = stops;
    }

    public List<String> stops() {
        return stops;
    }
}