package net.kgomc.zelda.ui.registry;

import net.kgomc.zelda.ui.annotation.*;
import net.kgomc.zelda.ui.config.MenuConfig;
import net.kgomc.zelda.ui.context.ClickContext;
import net.kgomc.zelda.ui.context.UIStateBag;
import net.kgomc.zelda.ui.context.ViewContext;
import net.kgomc.zelda.ui.handler.BuiltHandler;
import net.kgomc.zelda.ui.handler.HandlerBuilder;
import net.kgomc.zelda.ui.handler.ZeldaMenuHandler;
import net.kgomc.zelda.ui.placeholder.PlaceholderService;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * Central registry for all Zelda menus.
 *
 * <p>Supports both annotation-driven and programmatic handler registration.
 * Thread-safe for concurrent reads — writes happen only at startup.</p>
 */
public final class UIRegistry {

    /** Registered entries: menuName → (config, handler) */
    private final Map<String, MenuEntry> entries = new ConcurrentHashMap<>();

    private final PlaceholderService placeholderService;
    private final Logger             logger;

    public UIRegistry(PlaceholderService placeholderService, Logger logger) {
        this.placeholderService = placeholderService;
        this.logger             = logger;
    }

    // -----------------------------------------------------------------------
    // Registration — annotation style
    // -----------------------------------------------------------------------

    /**
     * Registers a menu config paired with an annotation-driven handler instance.
     * The handler's class must be annotated with {@code @ZeldaUI}.
     */
    public void register(MenuConfig config, ZeldaMenuHandler handler) {
        ZeldaUI annotation = handler.getClass().getAnnotation(ZeldaUI.class);
        if (annotation == null) {
            throw new IllegalArgumentException(
                    handler.getClass().getSimpleName() + " is missing @ZeldaUI annotation.");
        }
        register(annotation.value(), config, scanHandler(handler));
    }

    /**
     * Registers a menu config with an annotation-driven handler,
     * using a specific name (overrides the @ZeldaUI annotation value).
     */
    public void register(String name, MenuConfig config, ZeldaMenuHandler handler) {
        register(name, config, scanHandler(handler));
    }

    // -----------------------------------------------------------------------
    // Registration — programmatic style
    // -----------------------------------------------------------------------

    /**
     * Registers a menu config with a programmatically-built handler.
     */
    public void register(String name, MenuConfig config, BuiltHandler handler) {
        if (entries.containsKey(name)) {
            throw new IllegalStateException("Menu already registered: " + name);
        }
        entries.put(name, new MenuEntry(name, config, handler));
        logger.info("[Zelda/UI] Registered menu: " + name);
    }

    /**
     * Registers a menu config with a {@link HandlerBuilder}.
     */
    public void register(String name, MenuConfig config, HandlerBuilder builder) {
        register(name, config, builder.build());
    }

    // -----------------------------------------------------------------------
    // Open
    // -----------------------------------------------------------------------

    /**
     * Opens the named menu for a player with a fresh state bag.
     */
    public void open(String name, Player player) {
        open(name, player, new UIStateBag());
    }

    /**
     * Opens the named menu for a player with a pre-populated state bag.
     */
    public void open(String name, Player player, UIStateBag state) {
        MenuEntry entry = entries.get(name);
        if (entry == null) {
            throw new IllegalArgumentException("No menu registered with name: " + name);
        }
        ViewContext viewCtx = new ViewContext(player, state, name);
        entry.handler().fireOpen(viewCtx);
        MenuFactory.open(entry, viewCtx, placeholderService);
    }

    // -----------------------------------------------------------------------
    // Lookup
    // -----------------------------------------------------------------------

    public Optional<MenuEntry> find(String name) {
        return Optional.ofNullable(entries.get(name));
    }

    public boolean isRegistered(String name) {
        return entries.containsKey(name);
    }

    // -----------------------------------------------------------------------
    // Annotation scanning
    // -----------------------------------------------------------------------

    /**
     * Reflects over a {@link ZeldaMenuHandler} subclass and produces a
     * {@link BuiltHandler} from its annotated methods.
     */
    private BuiltHandler scanHandler(ZeldaMenuHandler handler) {
        HandlerBuilder builder = HandlerBuilder.create();
        Class<?> clazz = handler.getClass();

        // Wire lifecycle
        builder.onOpen(ctx -> handler.onOpen(ctx));
        builder.onClose(ctx -> handler.onClose(ctx));

        for (Method method : clazz.getDeclaredMethods()) {
            method.setAccessible(true);

            // @ShowIf
            ShowIf showIf = method.getAnnotation(ShowIf.class);
            if (showIf != null) {
                builder.showIf(showIf.value(), ctx -> {
                    try { return (boolean) method.invoke(handler, ctx); }
                    catch (Exception e) { return false; }
                });
            }

            // @Placeholder
            Placeholder placeholder = method.getAnnotation(Placeholder.class);
            if (placeholder != null) {
                builder.placeholder(placeholder.value(), ctx -> {
                    try { return (String) method.invoke(handler, ctx); }
                    catch (Exception e) { return ""; }
                });
            }

            // @OnClick — any click
            OnClick onClick = method.getAnnotation(OnClick.class);
            if (onClick != null) {
                for (String code : onClick.value()) {
                    builder.onClick(code, ctx -> {
                        try { method.invoke(handler, ctx); }
                        catch (Exception e) { logger.warning("[Zelda/UI] Click handler error: " + e.getMessage()); }
                    });
                }
            }

            // @OnLeftClick
            OnLeftClick onLeft = method.getAnnotation(OnLeftClick.class);
            if (onLeft != null) {
                for (String code : onLeft.value()) {
                    builder.onLeftClick(code, ctx -> {
                        try { method.invoke(handler, ctx); }
                        catch (Exception e) { logger.warning("[Zelda/UI] Left-click handler error: " + e.getMessage()); }
                    });
                }
            }

            // @OnRightClick
            OnRightClick onRight = method.getAnnotation(OnRightClick.class);
            if (onRight != null) {
                for (String code : onRight.value()) {
                    builder.onRightClick(code, ctx -> {
                        try { method.invoke(handler, ctx); }
                        catch (Exception e) { logger.warning("[Zelda/UI] Right-click handler error: " + e.getMessage()); }
                    });
                }
            }

            // @OnMiddleClick
            OnMiddleClick onMiddle = method.getAnnotation(OnMiddleClick.class);
            if (onMiddle != null) {
                for (String code : onMiddle.value()) {
                    builder.onMiddleClick(code, ctx -> {
                        try { method.invoke(handler, ctx); }
                        catch (Exception e) { logger.warning("[Zelda/UI] Middle-click handler error: " + e.getMessage()); }
                    });
                }
            }
        }

        return builder.build();
    }
}