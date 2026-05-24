package net.kgomc.zelda.messaging.target;

import com.velocitypowered.api.proxy.Player;
import net.kgomc.zelda.messaging.channel.*;
import net.kyori.adventure.text.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * {@link ITarget} wrapping a Velocity {@link Player}.
 *
 * <p>Velocity's {@link Player} implements Adventure's {@code Audience} natively,
 * so chat is straightforward. Player-only channels that require a server-side
 * implementation (boss bar, sound) are no-ops on Velocity — those packets are
 * handled by the backend server.</p>
 *
 * <pre>{@code
 * ITarget target = VelocityPlayerTarget.of(velocityPlayer);
 * messaging.send(target, "<primary>Hello from the proxy!</primary>");
 * }</pre>
 */
public final class VelocityPlayerTarget implements ITarget {

    private final Player player;

    private VelocityPlayerTarget(Player player) {
        this.player = player;
    }

    public static VelocityPlayerTarget of(Player player) {
        if (player == null) throw new IllegalArgumentException("player must not be null");
        return new VelocityPlayerTarget(player);
    }

    @Override public Optional<UUID> getUUID() { return Optional.of(player.getUniqueId()); }
    @Override public boolean isPlayer()        { return true; }

    @Override
    public void sendMessage(Component message) {
        player.sendMessage(message);
    }

    @Override
    public void sendActionBar(Component message) {
        player.sendActionBar(message);
    }

    @Override
    public void sendTitle(TitleData data, Component title, Component subtitle) {
        Component sub = subtitle != null ? subtitle : Component.empty();
        net.kyori.adventure.title.Title.Times times =
                net.kyori.adventure.title.Title.Times.times(data.fadeIn(), data.stay(), data.fadeOut());
        player.showTitle(net.kyori.adventure.title.Title.title(title, sub, times));
    }

    /**
     * No-op on Velocity — boss bars are rendered server-side.
     * Send via your backend Paper server instead.
     */
    @Override
    public void showBossBar(BossBarData data, Component name) {}

    /**
     * No-op on Velocity — sounds are played server-side.
     * Send via your backend Paper server instead.
     */
    @Override
    public void playSound(SoundData data) {}

    /** Returns the underlying Velocity player. */
    public Player unwrap() { return player; }
}