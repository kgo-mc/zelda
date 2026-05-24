package net.kgomc.zelda.messaging.target;

import net.kgomc.zelda.messaging.channel.*;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * {@link ITarget} wrapping a Bukkit {@link CommandSender}.
 *
 * <p>Automatically delegates player-only channels if the sender is a {@link Player}.
 * Console senders silently ignore player-only channels.</p>
 *
 * <pre>{@code
 * ITarget sender = CommandSenderTarget.of(commandSender);
 * }</pre>
 */
public final class CommandSenderTarget implements ITarget {

    private final CommandSender sender;

    /** Delegate for player-only channels when sender is a Player, null otherwise */
    private @Nullable final PlayerTarget playerDelegate;

    private CommandSenderTarget(CommandSender sender) {
        this.sender          = sender;
        this.playerDelegate  = sender instanceof Player p ? PlayerTarget.of(p) : null;
    }

    public static CommandSenderTarget of(CommandSender sender) {
        if (sender == null) throw new IllegalArgumentException("sender must not be null");
        return new CommandSenderTarget(sender);
    }

    @Override
    public Optional<UUID> getUUID() {
        return playerDelegate != null ? playerDelegate.getUUID() : Optional.empty();
    }

    @Override public boolean isPlayer() { return playerDelegate != null; }

    @Override
    public void sendMessage(Component message) {
        sender.sendMessage(message);
    }

    @Override
    public void sendActionBar(Component message) {
        if (playerDelegate != null) playerDelegate.sendActionBar(message);
    }

    @Override
    public void sendTitle(TitleData data, Component title, Component subtitle) {
        if (playerDelegate != null) playerDelegate.sendTitle(data, title, subtitle);
    }

    @Override
    public void showBossBar(BossBarData data, Component name) {
        if (playerDelegate != null) playerDelegate.showBossBar(data, name);
    }

    @Override
    public void playSound(SoundData data) {
        if (playerDelegate != null) playerDelegate.playSound(data);
    }
}