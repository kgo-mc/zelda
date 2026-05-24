package net.kgomc.zelda.ui.registry;

import net.kgomc.zelda.ui.config.MenuConfig;
import net.kgomc.zelda.ui.handler.BuiltHandler;

/**
 * Internal pairing of a menu's config and its resolved handler.
 */
public record MenuEntry(
        String      name,
        MenuConfig  config,
        BuiltHandler handler
) {}