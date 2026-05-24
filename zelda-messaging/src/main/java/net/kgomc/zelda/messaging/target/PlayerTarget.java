package net.kgomc.zelda.messaging.target;

import net.kgomc.zelda.messaging.channel.*;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

/**
 * {@link ITarget} wrapping a Paper {@link Player}.
 */
public final class PlayerTarget implements ITarget {

    private final Player player;

    private PlayerTarget(Player player) {
        this.player = player;
    }

    public static PlayerTarget of(Player player) {
        if (player == null) throw new IllegalArgumentException("player must not be null");
        return new PlayerTarget(player);
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
        Title.Times times = Title.Times.times(data.fadeIn(), data.stay(), data.fadeOut());
        player.showTitle(Title.title(title, sub, times));
    }

    @Override
    public void showBossBar(BossBarData data, Component name) {
        BossBar bar = BossBar.bossBar(name, data.progress(), data.color(), data.overlay());
        player.showBossBar(bar);

        // Auto-hide after duration if set
        if (data.duration() != null) {
            var scheduler = player.getServer().getScheduler();
            scheduler.runTaskLater(
                    player.getServer().getPluginManager().getPlugins()[0], // best-effort; MessagingModule passes plugin
                    () -> player.hideBossBar(bar),
                    data.duration().toSeconds() * 20L
            );
        }
    }

    @Override
    public void playSound(SoundData data) {
        player.playSound(
                Sound.sound(Key.key(data.key()), data.source(), data.volume(), data.pitch())
        );
    }

    /** Returns the underlying Paper player. */
    public Player unwrap() { return player; }
}