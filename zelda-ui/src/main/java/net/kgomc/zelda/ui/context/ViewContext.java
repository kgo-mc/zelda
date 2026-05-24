package net.kgomc.zelda.ui.context;

import org.bukkit.entity.Player;

/**
 * Context passed to {@link net.kgomc.zelda.ui.annotation.ShowIf} and
 * {@link net.kgomc.zelda.ui.annotation.Placeholder} methods.
 *
 * <p>Contains the viewing player and the menu's mutable state bag.</p>
 */
public record ViewContext(
        Player player,
        UIStateBag state,
        String menuName
) {}