package net.kgomc.zelda.messaging.target;

import net.kgomc.zelda.messaging.channel.*;
import net.kyori.adventure.text.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Platform-agnostic message recipient.
 *
 * <p>Abstracts over Paper's {@code Player}, {@code CommandSender}, console,
 * and Velocity's {@code Player} so that the messaging module can send to any
 * of them without platform-specific branching at the call site.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * ITarget target = PlayerTarget.of(player);
 * messaging.send(target, "<primary>Hello!</primary>");
 * messaging.sendTitle(target, TitleData.of("<primary>Welcome</primary>", "<muted>To KGO MC</muted>"));
 * }</pre>
 */
public interface ITarget {

    /**
     * The player UUID — present for player targets, empty for console/sender.
     * Used by {@link net.kgomc.zelda.messaging.palette.ThemeProvider} to
     * resolve the active palette theme.
     */
    Optional<UUID> getUUID();

    /** Returns true if this target can receive player-only channels (action bar, title, boss bar, sound). */
    boolean isPlayer();

    /** Sends a chat message component. */
    void sendMessage(Component message);

    /**
     * Sends to the action bar. No-op for non-player targets — callers
     * should check {@link #isPlayer()} first if they care.
     */
    void sendActionBar(Component message);

    /** Sends a title. No-op for non-player targets. */
    void sendTitle(TitleData data, Component title, Component subtitle);

    /** Shows a boss bar. No-op for non-player targets. */
    void showBossBar(BossBarData data, Component name);

    /** Plays a sound. No-op for non-player targets. */
    void playSound(SoundData data);
}