package net.kgomc.zelda.ui.module;

import net.kgomc.zelda.core.context.RuntimeKind;
import net.kgomc.zelda.core.context.ZeldaContext;
import net.kgomc.zelda.core.module.ZeldaModule;
import net.kgomc.zelda.ui.placeholder.PlaceholderService;
import net.kgomc.zelda.ui.registry.UIRegistry;

/**
 * Zelda UI module.
 *
 * <p>Server-only — throws on Velocity since inventory UIs don't exist on proxies.</p>
 *
 * <h2>Setup</h2>
 * <pre>{@code
 * Zelda.builder()
 *     .withUI()
 *     .initialize(new PaperPluginAdapter(this));
 *
 * UIModule ui = ZeldaContext.get().getRegistry()
 *     .find(UIModule.class).orElseThrow();
 *
 * // Annotation style
 * ui.getRegistry().register(menuConfig, new ShopMenuHandler());
 *
 * // Programmatic style
 * ui.getRegistry().register("shop", menuConfig,
 *     HandlerBuilder.create()
 *         .onClick("BUY", ctx -> { ... })
 *         .build()
 * );
 *
 * // Open for a player
 * ui.getRegistry().open("shop", player);
 * }</pre>
 */
public final class UIModule implements ZeldaModule {

    private UIRegistry registry;

    @Override
    public String getName() {
        return "ui";
    }

    @Override
    public void onEnable(ZeldaContext context) {
        // Guard: UI only works on server, not proxy
        if (context.getPlugin().getRuntimeKind() != RuntimeKind.SERVER) {
            throw new IllegalStateException(
                    "[Zelda/UI] UIModule can only run on a server (RuntimeKind.SERVER). " +
                            "Velocity proxies cannot open inventory UIs."
            );
        }

        context.getLogger().info("[Zelda/UI] Initialising UI registry...");
        PlaceholderService placeholderService = new PlaceholderService(context.getLogger());
        this.registry = new UIRegistry(placeholderService, context.getLogger());
        context.getLogger().info("[Zelda/UI] Ready.");
    }

    @Override
    public void onDisable() {
        // InvUI handles open window cleanup internally
    }

    /**
     * Returns the UI registry for registering and opening menus.
     *
     * @throws IllegalStateException if called before the module is enabled
     */
    public UIRegistry getRegistry() {
        if (registry == null) {
            throw new IllegalStateException("UIModule is not yet enabled.");
        }
        return registry;
    }
}