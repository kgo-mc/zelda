package net.kgomc.zelda.ui.handler;

import net.kgomc.zelda.ui.context.ClickContext;
import net.kgomc.zelda.ui.context.ViewContext;

/**
 * Base class for annotation-driven menu handlers.
 *
 * <p>Extend this class and annotate methods with
 * {@link net.kgomc.zelda.ui.annotation.OnClick @OnClick},
 * {@link net.kgomc.zelda.ui.annotation.ShowIf @ShowIf},
 * {@link net.kgomc.zelda.ui.annotation.Placeholder @Placeholder}, etc.</p>
 *
 * <pre>{@code
 * @ZeldaUI("shop-menu")
 * public class ShopMenuHandler extends ZeldaMenuHandler {
 *
 *     @ShowIf("isVip")
 *     public boolean isVip(ViewContext ctx) {
 *         return ctx.player().hasPermission("server.vip");
 *     }
 *
 *     @Placeholder("player_coins")
 *     public String coins(ViewContext ctx) {
 *         return String.valueOf(economy.getCoins(ctx.player()));
 *     }
 *
 *     @OnLeftClick("BUY")
 *     public void onBuy(ClickContext ctx) {
 *         economy.deduct(ctx.player(), 100);
 *         ctx.close();
 *     }
 *
 *     @OnClick({"PREV", "NEXT"})   // one handler for multiple slot codes
 *     public void onNavigate(ClickContext ctx) {
 *         int page = ctx.state().get("page", Integer.class).orElse(0);
 *         ctx.state().put("page", ctx.slotCode().equals("NEXT") ? page + 1 : page - 1);
 *     }
 * }
 * }</pre>
 *
 * <p>Optionally override {@link #onOpen} and {@link #onClose} for lifecycle hooks.</p>
 */
public abstract class ZeldaMenuHandler {

    /**
     * Called when the menu is opened for a player.
     * Use to pre-populate the state bag.
     */
    public void onOpen(ViewContext ctx) {}

    /**
     * Called when the player closes the menu.
     */
    public void onClose(ViewContext ctx) {}
}