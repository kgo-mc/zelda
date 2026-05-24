package net.kgomc.zelda.ui.annotation;

import java.lang.annotation.*;

/**
 * Marks a method as a condition evaluator for showIf / showIfFirst entries.
 *
 * <p>Method signature: {@code boolean method(ViewContext ctx)}</p>
 *
 * <pre>{@code
 * @ShowIf("isVip")
 * public boolean isVip(ViewContext ctx) {
 *     return ctx.player().hasPermission("server.vip");
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ShowIf {
    /** Must match the {@code showIf} string in the YAML condition entry. */
    String value();
}