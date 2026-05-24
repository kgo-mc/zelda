package net.kgomc.zelda.ui.context;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/**
 * Context passed to click handler methods
 * ({@link net.kgomc.zelda.ui.annotation.OnClick},
 *  {@link net.kgomc.zelda.ui.annotation.OnLeftClick}, etc.).
 */
public record ClickContext(
        Player player,
        UIStateBag state,
        String menuName,
        String slotCode,
        ClickType clickType,
        int slot
) {
    /** Closes the menu for this player. */
    public void close() {
        player.closeInventory();
    }

    /** Convenience — is this a left click? */
    public boolean isLeftClick()   { return clickType == ClickType.LEFT || clickType == ClickType.SHIFT_LEFT; }

    /** Convenience — is this a right click? */
    public boolean isRightClick()  { return clickType == ClickType.RIGHT || clickType == ClickType.SHIFT_RIGHT; }

    /** Convenience — is this a middle click? */
    public boolean isMiddleClick() { return clickType == ClickType.MIDDLE; }

    /** Convenience — is shift held? */
    public boolean isShift()       { return clickType == ClickType.SHIFT_LEFT || clickType == ClickType.SHIFT_RIGHT; }
}