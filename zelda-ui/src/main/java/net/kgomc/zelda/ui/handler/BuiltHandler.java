package net.kgomc.zelda.ui.handler;

import net.kgomc.zelda.ui.context.ClickContext;
import net.kgomc.zelda.ui.context.ViewContext;
import org.bukkit.event.inventory.ClickType;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Resolved handler produced by {@link HandlerBuilder#build()}.
 * Also produced internally by the registry when scanning a
 * {@link ZeldaMenuHandler} subclass for annotations.
 *
 * <p>This is the single internal representation the {@code MenuFactory}
 * works with — regardless of whether the handler came from annotations
 * or a builder.</p>
 */
public final class BuiltHandler {

    private final Map<String, Predicate<ViewContext>>                             showIfConditions;
    private final Map<String, List<Map.Entry<ClickType, Consumer<ClickContext>>>> clickHandlers;
    private final Map<String, Function<ViewContext, String>>                      placeholders;
    private final Consumer<ViewContext>                                           onOpen;
    private final Consumer<ViewContext>                                           onClose;

    BuiltHandler(
            Map<String, Predicate<ViewContext>> showIfConditions,
            Map<String, List<Map.Entry<ClickType, Consumer<ClickContext>>>> clickHandlers,
            Map<String, Function<ViewContext, String>> placeholders,
            Consumer<ViewContext> onOpen,
            Consumer<ViewContext> onClose
    ) {
        this.showIfConditions = showIfConditions;
        this.clickHandlers    = clickHandlers;
        this.placeholders     = placeholders;
        this.onOpen           = onOpen;
        this.onClose          = onClose;
    }

    // -----------------------------------------------------------------------
    // Evaluation
    // -----------------------------------------------------------------------

    /**
     * Evaluates a named showIf condition.
     *
     * @return true if the condition passes, or if no condition with that name is registered
     */
    public boolean evaluateCondition(String name, ViewContext ctx) {
        Predicate<ViewContext> condition = showIfConditions.get(name);
        return condition == null || condition.test(ctx);
    }

    /**
     * Resolves a placeholder key — handler-local placeholders take priority over PAPI.
     */
    public Optional<String> resolvePlaceholder(String key, ViewContext ctx) {
        Function<ViewContext, String> resolver = placeholders.get(key);
        return resolver == null ? Optional.empty() : Optional.of(resolver.apply(ctx));
    }

    /**
     * Fires matching click handlers for the given slot code and click type.
     */
    public void fireClick(ClickContext ctx) {
        List<Map.Entry<ClickType, Consumer<ClickContext>>> handlers =
                clickHandlers.get(ctx.slotCode());
        if (handlers == null) return;

        for (var entry : handlers) {
            ClickType required = entry.getKey();
            // UNKNOWN = any click (set by HandlerBuilder.onClick)
            if (required == ClickType.UNKNOWN || required == ctx.clickType()) {
                entry.getValue().accept(ctx);
            }
        }
    }

    public void fireOpen(ViewContext ctx)  { onOpen.accept(ctx); }
    public void fireClose(ViewContext ctx) { onClose.accept(ctx); }

    public boolean hasClickHandler(String slotCode) {
        return clickHandlers.containsKey(slotCode);
    }
}