package net.kgomc.zelda.ui.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;

import java.util.List;

/**
 * Full definition of a single inventory slot item.
 *
 * <p>Example YAML:</p>
 * <pre>{@code
 * items:
 *   X:
 *     material: PLAYER_HEAD
 *     name: "<gold>{player_name}'s Profile"
 *     lore:
 *       - "<gray>Coins: <yellow>{player_coins}"
 *       - "<gray>Rank: <green>{player_rank}"
 *     count: 1
 *     enchanted: false
 *     showIfFirst:
 *       - showIf: isVip
 *         overrides:
 *           material: DIAMOND
 *           name: "<gold>VIP Profile"
 *       - overrides:
 *           material: PLAYER_HEAD
 *     showIf:
 *       - showIf: isOnline
 *         overrides:
 *           lore:
 *             - "<green>● Online"
 *     frames: []          # non-empty enables animation
 * }</pre>
 */
@Configuration
public class ItemConfig {

    @Comment("Material name (Bukkit Material enum). Use PLAYER_HEAD for skull items.")
    private String material = "STONE";

    @Comment("Display name. Supports MiniMessage formatting and {placeholders}.")
    private String name = null;

    @Comment("Lore lines. Each supports MiniMessage and {placeholders}.")
    private List<String> lore = List.of();

    @Comment("Stack size. Supports {placeholders} that resolve to integers.")
    private String count = "1";

    @Comment("Whether to show the enchantment glint.")
    private boolean enchanted = false;

    @Comment("Custom model data value. -1 = not set.")
    private int customModelData = -1;

    @Comment({
            "showIfFirst — evaluated top to bottom, first passing condition wins.",
            "Sets the primary item variant. Only one block applies."
    })
    private List<ConditionConfig> showIfFirst = List.of();

    @Comment({
            "showIf — ALL passing conditions have their overrides merged on top of showIfFirst.",
            "Use for additive decorations (extra lore, enchanted flag, etc.)"
    })
    private List<ConditionConfig> showIf = List.of();

    @Comment({
            "Animation frames. If non-empty, the item cycles through these frames.",
            "showIf/showIfFirst are evaluated once on open, not per frame."
    })
    private List<FrameConfig> frames = List.of();

    public String material() {
        return material;
    }

    public String name() {
        return name;
    }

    public List<String> lore() {
        return lore;
    }

    public String count() {
        return count;
    }

    public boolean enchanted() {
        return enchanted;
    }

    public int customModelData() {
        return customModelData;
    }

    public List<ConditionConfig> showIfFirst() {
        return showIfFirst;
    }

    public List<ConditionConfig> showIf() {
        return showIf;
    }

    public List<FrameConfig> frames() {
        return frames;
    }

    public ItemConfig setMaterial(String material) {
        this.material = material;
        return this;
    }

    public ItemConfig setName(String name) {
        this.name = name;
        return this;
    }

    public ItemConfig setLore(List<String> lore) {
        this.lore = lore;
        return this;
    }

    public ItemConfig setCount(String count) {
        this.count = count;
        return this;
    }

    public ItemConfig setEnchanted(boolean enchanted) {
        this.enchanted = enchanted;
        return this;
    }

    public ItemConfig setCustomModelData(int customModelData) {
        this.customModelData = customModelData;
        return this;
    }

    public ItemConfig setShowIfFirst(List<ConditionConfig> showIfFirst) {
        this.showIfFirst = showIfFirst;
        return this;
    }

    public ItemConfig setShowIf(List<ConditionConfig> showIf) {
        this.showIf = showIf;
        return this;
    }

    public ItemConfig setFrames(List<FrameConfig> frames) {
        this.frames = frames;
        return this;
    }
}