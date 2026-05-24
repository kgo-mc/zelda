package net.kgomc.zelda.ui.config;

import de.exlll.configlib.Configuration;

import java.util.List;

/**
 * One frame in an animated item.
 *
 * <p>Example YAML:</p>
 * <pre>{@code
 * frames:
 *   - material: RED_WOOL
 *     name: "<red>Frame 1"
 *     delay: 10        # ticks between this frame and the next
 *   - material: GREEN_WOOL
 *     name: "<green>Frame 2"
 *     delay: 10
 * }</pre>
 */
@Configuration
public class FrameConfig {

    /** Material for this frame. */
    private String material = "STONE";

    /** Display name for this frame. Supports placeholders and MiniMessage. */
    private String name = null;

    /** Lore for this frame. Supports placeholders and MiniMessage. */
    private List<String> lore = List.of();

    /** Ticks to display this frame before advancing to the next. Default: 20 (1 second). */
    private int delay = 20;

    public String material() {
        return material;
    }

    public String name() {
        return name;
    }

    public List<String> lore() {
        return lore;
    }

    public int delay() {
        return delay;
    }
}