package net.kgomc.zelda.ui.annotation;

import java.lang.annotation.*;

/**
 * Marks a class as a Zelda UI handler, binding it to a named menu config.
 *
 * <p>The value must match the menu name registered with
 * {@link net.kgomc.zelda.ui.registry.UIRegistry}.</p>
 *
 * <pre>{@code
 * @ZeldaUI("shop-menu")
 * public class ShopMenuHandler extends ZeldaMenuHandler {
 *
 *     @ShowIf("canAfford")
 *     public boolean canAfford(ViewContext ctx) {
 *         return economy.getCoins(ctx.player()) >= 100;
 *     }
 *
 *     @OnClick("X")
 *     public void onBuyClick(ClickContext ctx) {
 *         economy.deduct(ctx.player(), 100);
 *         ctx.player().sendMessage("Purchased!");
 *     }
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ZeldaUI {
    /** The menu name this handler is bound to. */
    String value();
}