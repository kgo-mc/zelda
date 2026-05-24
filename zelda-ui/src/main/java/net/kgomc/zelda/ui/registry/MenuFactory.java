package net.kgomc.zelda.ui.registry;

import net.kgomc.zelda.ui.config.*;
import net.kgomc.zelda.ui.context.ClickContext;
import net.kgomc.zelda.ui.context.ViewContext;
import net.kgomc.zelda.ui.placeholder.PlaceholderService;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import xyz.xenondevs.inventoryaccess.component.AdventureComponentWrapper;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.gui.PagedGui;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.impl.SimpleItem;
import xyz.xenondevs.invui.window.Window;

import java.util.*;

/**
 * Wires a {@link MenuConfig} and {@link net.kgomc.zelda.ui.handler.BuiltHandler}
 * into an InvUI {@link Gui} and opens it for a player.
 *
 * <p>Execution order per slot:</p>
 * <ol>
 *   <li>Resolve {@code showIfFirst} → first passing condition's overrides set the base</li>
 *   <li>Merge all passing {@code showIf} overrides on top</li>
 *   <li>Resolve placeholders in name and lore</li>
 *   <li>Build InvUI item, wire click handler if present</li>
 * </ol>
 */
final class MenuFactory {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private MenuFactory() {}

    static void open(MenuEntry entry, ViewContext ctx, PlaceholderService placeholders) {
        MenuConfig config = entry.config();
        Player     player = ctx.player();

        net.kgomc.zelda.core.context.ZeldaPlugin zeldaPlugin =
                net.kgomc.zelda.core.context.ZeldaContext.get().getPlugin();

        // Parse layout into slot → code map
        Map<Integer, String> slotMap = parseLayout(config);

        // Build all items
        List<Item> items = new ArrayList<>(Collections.nCopies(slotMap.isEmpty() ? 0 :
                Collections.max(slotMap.keySet()) + 1, null));

        int totalSlots = config.rows() * 9;
        Item[] slotItems = new Item[totalSlots];

        for (Map.Entry<Integer, String> slotEntry : slotMap.entrySet()) {
            int    slotIndex = slotEntry.getKey();
            String code      = slotEntry.getValue();

            if (code == null || code.equals(".") || code.equals(" ")) continue;

            ItemConfig itemConfig = resolveItemConfig(code, config);
            if (itemConfig == null) continue;

            ItemConfig resolved = applyConditions(itemConfig, ctx, entry);
            Item item = buildItem(code, resolved, ctx, entry, placeholders, zeldaPlugin);

            if (slotIndex < totalSlots) slotItems[slotIndex] = item;
        }

        // Build InvUI Gui
        Gui gui = config.paginated()
                ? buildPagedGui(config, slotItems)
                : buildNormalGui(config, slotItems);

        // Open window
        Window.single()
                .setViewer(player)
                .setTitle(new AdventureComponentWrapper(MM.deserialize(placeholders.resolve(config.title(), player))))
                .setGui(gui)
                .addCloseHandler(() -> entry.handler().fireClose(ctx))
                .build()
                .open();
    }

    // -----------------------------------------------------------------------
    // Layout parsing
    // -----------------------------------------------------------------------

    /**
     * Parses the layout string array into a slot index → code map.
     * Each row string is space-separated: "X . Y . B"
     */
    private static Map<Integer, String> parseLayout(MenuConfig config) {
        Map<Integer, String> map = new LinkedHashMap<>();
        List<String> layout = config.layout();

        for (int row = 0; row < layout.size(); row++) {
            String[] tokens = layout.get(row).trim().split("\\s+");
            for (int col = 0; col < tokens.length && col < 9; col++) {
                int slotIndex = row * 9 + col;
                map.put(slotIndex, tokens[col]);
            }
        }
        return map;
    }

    // -----------------------------------------------------------------------
    // Item config resolution
    // -----------------------------------------------------------------------

    /**
     * Looks up the ItemConfig for a slot code from the items map.
     */
    private static ItemConfig resolveItemConfig(String code, MenuConfig config) {
        return config.items().get(code);
    }

    // -----------------------------------------------------------------------
    // Condition evaluation
    // -----------------------------------------------------------------------

    /**
     * Applies showIfFirst then showIf conditions, returning the final merged ItemConfig.
     *
     * Priority: showIfFirst sets the variant → showIf decorates it.
     */
    private static ItemConfig applyConditions(ItemConfig base, ViewContext ctx, MenuEntry entry) {
        // Step 1 — showIfFirst: find first passing condition
        Map<String, Object> firstWinnerOverrides = null;
        for (ConditionConfig cond : base.showIfFirst()) {
            if (conditionPasses(cond, ctx, entry)) {
                firstWinnerOverrides = cond.overrides();
                break;
            }
        }

        // Step 2 — showIf: collect all passing overrides
        List<Map<String, Object>> showIfOverrides = new ArrayList<>();
        for (ConditionConfig cond : base.showIf()) {
            if (conditionPasses(cond, ctx, entry)) {
                showIfOverrides.add(cond.overrides());
            }
        }

        if (firstWinnerOverrides == null && showIfOverrides.isEmpty()) return base;

        // Merge: base → showIfFirst winner → showIf overrides (in order)
        return mergeOverrides(base, firstWinnerOverrides, showIfOverrides);
    }

    private static boolean conditionPasses(ConditionConfig cond, ViewContext ctx, MenuEntry entry) {
        String showIf = cond.showIf();
        if (showIf == null || showIf.isBlank()) return true; // unconditional default
        return entry.handler().evaluateCondition(showIf, ctx);
    }

    /**
     * Merges overrides onto a base ItemConfig, producing a new instance.
     * showIfFirst winner is applied first, then each showIf override on top.
     */
    private static ItemConfig mergeOverrides(
            ItemConfig base,
            Map<String, Object> firstWinner,
            List<Map<String, Object>> showIfOverrides
    ) {
        // Start from base values
        String       material        = base.material();
        String       name            = base.name();
        List<String> lore            = new ArrayList<>(base.lore());
        String       count           = base.count();
        boolean      enchanted       = base.enchanted();
        int          customModelData = base.customModelData();

        // Apply showIfFirst winner
        if (firstWinner != null) {
            material        = getString(firstWinner, "material",        material);
            name            = getString(firstWinner, "name",            name);
            lore            = getStringList(firstWinner, "lore",        lore);
            count           = getString(firstWinner, "count",           count);
            enchanted       = getBoolean(firstWinner, "enchanted",      enchanted);
            customModelData = getInt(firstWinner,    "customModelData", customModelData);
        }

        // Apply each showIf override (cumulative)
        for (Map<String, Object> overrides : showIfOverrides) {
            material        = getString(overrides, "material",        material);
            name            = getString(overrides, "name",            name);
            lore            = getStringList(overrides, "lore",        lore);
            count           = getString(overrides, "count",           count);
            enchanted       = getBoolean(overrides, "enchanted",      enchanted);
            customModelData = getInt(overrides,    "customModelData", customModelData);
        }

        // Build merged config using setters
        ItemConfig merged = new ItemConfig()
                .setMaterial(material)
                .setName(name)
                .setLore(lore)
                .setCount(count)
                .setEnchanted(enchanted)
                .setCustomModelData(customModelData)
                .setFrames(base.frames())     // frames not overridable per condition
                .setShowIf(List.of())         // conditions already evaluated
                .setShowIfFirst(List.of());
        return merged;
    }

    // -----------------------------------------------------------------------
    // InvUI item building
    // -----------------------------------------------------------------------

    private static Item buildItem(
            String code, ItemConfig config, ViewContext ctx,
            MenuEntry entry, PlaceholderService placeholders,
            net.kgomc.zelda.core.context.ZeldaPlugin zeldaPlugin
    ) {
        if (!config.frames().isEmpty()) {
            return buildAnimatedItem(code, config, ctx, entry, placeholders, zeldaPlugin);
        }

        ItemStack stack = buildItemStack(config, ctx, entry, placeholders);
        boolean hasHandler = entry.handler().hasClickHandler(code);

        if (!hasHandler) return new SimpleItem(uuid -> stack);

        return new SimpleItem(uuid -> stack) {
            @Override
            public void handleClick(ClickType clickType, Player player,
                                    org.bukkit.event.inventory.InventoryClickEvent event) {
                event.setCancelled(true);
                ClickContext clickCtx = new ClickContext(
                        player, ctx.state(), ctx.menuName(),
                        code, clickType, event.getSlot()
                );
                entry.handler().fireClick(clickCtx);
            }
        };
    }

    private static Item buildAnimatedItem(
            String code, ItemConfig config, ViewContext ctx,
            MenuEntry entry, PlaceholderService placeholders,
            net.kgomc.zelda.core.context.ZeldaPlugin zeldaPlugin
    ) {
        List<FrameConfig> frames = config.frames();

        return new xyz.xenondevs.invui.item.impl.AbstractItem() {

            private int currentFrame = 0;
            private net.kgomc.zelda.core.context.ZeldaTask task;

            {
                scheduleFrameCycler();
            }

            private void scheduleFrameCycler() {
                long firstDelay = frames.get(0).delay();
                task = zeldaPlugin.runTaskTimer(() -> {
                    currentFrame = (currentFrame + 1) % frames.size();
                    notifyWindows();
                }, firstDelay, frames.get(currentFrame).delay());
            }

            @Override
            public xyz.xenondevs.invui.item.ItemProvider getItemProvider() {
                FrameConfig frame = frames.get(currentFrame);
                ItemConfig frameConfig = new ItemConfig()
                        .setMaterial(frame.material())
                        .setName(frame.name() != null ? frame.name() : config.name())
                        .setLore(frame.lore().isEmpty() ? config.lore() : frame.lore())
                        .setEnchanted(config.enchanted())
                        .setCustomModelData(config.customModelData());
                return uuid -> buildItemStack(frameConfig, ctx, entry, placeholders);
            }

            @Override
            public void handleClick(org.bukkit.event.inventory.ClickType clickType,
                                    org.bukkit.entity.Player player,
                                    org.bukkit.event.inventory.InventoryClickEvent event) {
                event.setCancelled(true);
                if (!entry.handler().hasClickHandler(code)) return;
                ClickContext clickCtx = new ClickContext(
                        player, ctx.state(), ctx.menuName(),
                        code, clickType, event.getSlot()
                );
                entry.handler().fireClick(clickCtx);
            }
        };
    }

    private static ItemStack buildItemStack(
            ItemConfig config,
            ViewContext ctx,
            MenuEntry entry,
            PlaceholderService placeholders
    ) {
        Player player = ctx.player();

        // Resolve placeholders — handler-local first, then PAPI
        String resolvedName = resolveText(config.name(), ctx, entry, placeholders);
        List<String> resolvedLore = config.lore().stream()
                .map(line -> resolveText(line, ctx, entry, placeholders))
                .toList();

        // Count (may be a placeholder)
        int count = 1;
        try {
            String countStr = resolveText(config.count(), ctx, entry, placeholders);
            count = Math.max(1, Math.min(64, Integer.parseInt(countStr.trim())));
        } catch (NumberFormatException ignored) {}

        Material material = Material.matchMaterial(config.material().toUpperCase());
        if (material == null) material = Material.STONE;

        ItemStack stack = new ItemStack(material, count);
        ItemMeta  meta  = stack.getItemMeta();
        if (meta == null) return stack;

        if (resolvedName != null) {
            meta.displayName(MM.deserialize(resolvedName));
        }
        if (!resolvedLore.isEmpty()) {
            meta.lore(resolvedLore.stream().map(MM::deserialize).toList());
        }
        if (config.customModelData() > -1) {
            meta.setCustomModelData(config.customModelData());
        }
        if (config.enchanted()) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        stack.setItemMeta(meta);
        return stack;
    }

    private static String resolveText(String text, ViewContext ctx, MenuEntry entry, PlaceholderService placeholders) {
        if (text == null) return null;

        // Handler-local @Placeholder methods take priority
        // We do a simple pass: for each {key} check handler first, then PlaceholderService
        StringBuilder sb = new StringBuilder(text);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\{([^{}]+)}").matcher(text);
        List<String[]> replacements = new ArrayList<>();
        while (m.find()) {
            String key = m.group(1);
            Optional<String> local = entry.handler().resolvePlaceholder(key, ctx);
            String value = local.orElseGet(() ->
                    placeholders.resolve("{" + key + "}", ctx.player())
            );
            replacements.add(new String[]{m.group(0), value});
        }
        String result = text;
        for (String[] r : replacements) {
            result = result.replace(r[0], r[1]);
        }
        return result;
    }

    // -----------------------------------------------------------------------
    // InvUI Gui builders
    // -----------------------------------------------------------------------

    private static Gui buildNormalGui(MenuConfig config, Item[] slotItems) {
        Gui.Builder.Normal builder = Gui.normal()
                .setStructure(buildStructure(config.rows(), slotItems));
        return builder.build();
    }

    private static Gui buildPagedGui(MenuConfig config, Item[] slotItems) {
        // For paged GUIs, non-null items outside the content area become background
        return PagedGui.items()
                .setStructure(buildStructure(config.rows(), slotItems))
                .setContent(List.of()) // content items added separately by consumer
                .build();
    }

    private static String buildStructure(int rows, Item[] slotItems) {
        // InvUI structure string: each slot index maps to a character
        // We use a flat array approach — pass items directly by slot
        StringBuilder structure = new StringBuilder();
        for (int i = 0; i < rows * 9; i++) {
            structure.append(slotItems[i] != null ? (char)('a' + i) : 'x');
        }
        return structure.toString();
    }

    // -----------------------------------------------------------------------
    // Override map helpers
    // -----------------------------------------------------------------------

    private static String getString(Map<String, Object> map, String key, String def) {
        Object v = map.get(key);
        return v instanceof String s ? s : def;
    }

    @SuppressWarnings("unchecked")
    private static List<String> getStringList(Map<String, Object> map, String key, List<String> def) {
        Object v = map.get(key);
        return v instanceof List<?> l ? (List<String>) l : def;
    }

    private static boolean getBoolean(Map<String, Object> map, String key, boolean def) {
        Object v = map.get(key);
        return v instanceof Boolean b ? b : def;
    }

    private static int getInt(Map<String, Object> map, String key, int def) {
        Object v = map.get(key);
        return v instanceof Integer i ? i : def;
    }
}