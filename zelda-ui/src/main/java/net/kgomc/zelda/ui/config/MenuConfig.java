package net.kgomc.zelda.ui.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;

import java.util.List;
import java.util.Map;

/**
 * Top-level configuration for a single Zelda menu.
 *
 * <p>Each menu lives in its own YAML file, registered with
 * {@link net.kgomc.zelda.ui.registry.UIRegistry} by name.</p>
 *
 * <p>Full example — {@code menus/shop.yml}:</p>
 * <pre>{@code
 * type: CHEST
 * title: "<gold>Item Shop"
 * rows: 5
 * paginated: false
 *
 *
 * layout:
 *   - "B B B B B B B B B"
 *   - "B X . . Y . . X B"
 *   - "B . . . . . . . B"
 *   - "B X . . Y . . X B"
 *   - "B B B B B B B B B"
 *
 * items:
 *   X:
 *     material: DIAMOND
 *     name: "<aqua>Diamond"
 *     lore:
 *       - "<gray>Cost: <yellow>{shop_price_diamond}"
 *     showIfFirst:
 *       - showIf: canAfford
 *         overrides:
 *           enchanted: true
 *   Y:
 *     material: EMERALD
 *     name: "<green>Emerald"
 * }</pre>
 */
@Configuration
public class MenuConfig {

    @Comment("Menu/inventory type. See MenuType enum for all supported values.")
    private MenuType type = MenuType.CHEST;

    @Comment("Title displayed in the inventory header. Supports MiniMessage and {placeholders}.")
    private String title = "Menu";

    @Comment("Number of rows (only applicable for CHEST type). Valid: 1–6.")
    private int rows = 3;

    @Comment("Whether this menu uses InvUI's pagination system.")
    private boolean paginated = false;

    @Comment({
            "Layout — array of strings defining the slot grid.",
            "Each character maps to an item code or slot group code.",
            "Use '.' or ' ' for empty slots.",
            "Characters are space-separated: \"X . Y . X\""
    })
    private List<String> layout = List.of();

    @Comment({
            "Item definitions — keyed by the single-character codes used in layout.",
            "Slot group items are defined under slotGroups, not here."
    })
    private Map<String, ItemConfig> items = Map.of();

    public MenuType type() {
        return type;
    }

    public String title() {
        return title;
    }

    public int rows() {
        return rows;
    }

    public boolean paginated() {
        return paginated;
    }

    public List<String> layout() {
        return layout;
    }

    public Map<String, ItemConfig> items() {
        return items;
    }
}