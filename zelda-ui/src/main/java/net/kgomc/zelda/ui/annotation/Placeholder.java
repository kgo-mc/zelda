package net.kgomc.zelda.ui.annotation;

import java.lang.annotation.*;

/**
 * Marks a method as a custom placeholder resolver for this menu.
 *
 * <p>Method signature: {@code String method(ViewContext ctx)}</p>
 *
 * <p>The returned string replaces {@code {key}} in any item name or lore
 * within this menu, taking priority over PAPI placeholders.</p>
 *
 * <pre>{@code
 * @Placeholder("player_coins")
 * public String playerCoins(ViewContext ctx) {
 *     return String.valueOf(economy.getCoins(ctx.player()));
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Placeholder {
    /** The placeholder key (without curly braces). */
    String value();
}