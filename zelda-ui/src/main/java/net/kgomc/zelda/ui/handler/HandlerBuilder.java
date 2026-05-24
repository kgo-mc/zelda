package net.kgomc.zelda.ui.handler;

import net.kgomc.zelda.ui.context.ClickContext;
import net.kgomc.zelda.ui.context.ViewContext;
import org.bukkit.event.inventory.ClickType;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Fluent programmatic alternative to extending {@link ZeldaMenuHandler}.
 *
 * <p>Use when you want to define a menu handler inline or with lambdas
 * instead of annotation-driven subclasses.</p>
 *
 * <pre>{@code
 * uiRegistry.register("shop-menu",
 *     HandlerBuilder.create()
 *         .showIf("isVip", ctx -> ctx.player().hasPermission("server.vip"))
 *         .placeholder("player_coins", ctx -> String.valueOf(economy.getCoins(ctx.player())))
 *         .onClick("BUY", ctx -> {
 *             economy.deduct(ctx.player(), 100);
 *             ctx.close();
 *         })
 *         .onLeftClick("PREV", ctx -> {
 *             int page = ctx.state().get("page", Integer.class).orElse(0);
 *             ctx.state().put("page", Math.max(0, page - 1));
 *         })
 *         .onOpen(ctx -> ctx.state().put("page", 0))
 *         .build()
 * );
 * }</pre>
 */
public final class HandlerBuilder {

    // showIf conditions keyed by name
    private final Map<String, Predicate<ViewContext>> showIfConditions = new LinkedHashMap<>();

    // click handlers: slotCode → list of (clickType filter, handler)
    // null clickType = any click
    private final Map<String, List<Map.Entry<ClickType, Consumer<ClickContext>>>> clickHandlers = new LinkedHashMap<>();

    // placeholder resolvers keyed by placeholder name
    private final Map<String, Function<ViewContext, String>> placeholders = new LinkedHashMap<>();

    private Consumer<ViewContext> onOpen  = ctx -> {};
    private Consumer<ViewContext> onClose = ctx -> {};

    private HandlerBuilder() {}

    public static HandlerBuilder create() {
        return new HandlerBuilder();
    }

    // -----------------------------------------------------------------------
    // ShowIf
    // -----------------------------------------------------------------------

    public HandlerBuilder showIf(String name, Predicate<ViewContext> condition) {
        showIfConditions.put(name, condition);
        return this;
    }

    // -----------------------------------------------------------------------
    // Click handlers
    // -----------------------------------------------------------------------

    /** Fires for any click type on the given slot code(s). */
    public HandlerBuilder onClick(String slotCode, Consumer<ClickContext> handler) {
        return addClickHandler(slotCode, null, handler);
    }

    public HandlerBuilder onLeftClick(String slotCode, Consumer<ClickContext> handler) {
        return addClickHandler(slotCode, ClickType.LEFT, handler);
    }

    public HandlerBuilder onRightClick(String slotCode, Consumer<ClickContext> handler) {
        return addClickHandler(slotCode, ClickType.RIGHT, handler);
    }

    public HandlerBuilder onMiddleClick(String slotCode, Consumer<ClickContext> handler) {
        return addClickHandler(slotCode, ClickType.MIDDLE, handler);
    }

    // -----------------------------------------------------------------------
    // Placeholders
    // -----------------------------------------------------------------------

    public HandlerBuilder placeholder(String key, Function<ViewContext, String> resolver) {
        placeholders.put(key, resolver);
        return this;
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    public HandlerBuilder onOpen(Consumer<ViewContext> handler) {
        this.onOpen = handler;
        return this;
    }

    public HandlerBuilder onClose(Consumer<ViewContext> handler) {
        this.onClose = handler;
        return this;
    }

    // -----------------------------------------------------------------------
    // Build
    // -----------------------------------------------------------------------

    public BuiltHandler build() {
        return new BuiltHandler(
                Map.copyOf(showIfConditions),
                Map.copyOf(clickHandlers),
                Map.copyOf(placeholders),
                onOpen,
                onClose
        );
    }

    // -----------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------

    private HandlerBuilder addClickHandler(String slotCode, ClickType type, Consumer<ClickContext> handler) {
        clickHandlers
                .computeIfAbsent(slotCode, k -> new ArrayList<>())
                .add(Map.entry(type == null ? ClickType.UNKNOWN : type, handler));
        return this;
    }
}