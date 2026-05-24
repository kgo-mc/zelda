package net.kgomc.zelda.messaging.channel;

import net.kyori.adventure.bossbar.BossBar;

import java.time.Duration;

/**
 * Payload for {@link SendChannel#BOSS_BAR}.
 *
 * <pre>{@code
 * BossBarData.of("<primary>Event starting in 30s</primary>", 1.0f)
 * BossBarData.of("<error>Server restart</error>", 0.5f, BossBar.Color.RED, BossBar.Overlay.PROGRESS, Duration.ofSeconds(10))
 * }</pre>
 */
public record BossBarData(
        String name,
        float progress,               // 0.0 – 1.0
        BossBar.Color color,
        BossBar.Overlay overlay,
        Duration duration             // how long to show it, null = until explicitly removed
) {
    public static BossBarData of(String name, float progress) {
        return new BossBarData(name, progress, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS, null);
    }

    public static BossBarData of(String name, float progress,
                                 BossBar.Color color, BossBar.Overlay overlay, Duration duration) {
        return new BossBarData(name, progress, color, overlay, duration);
    }
}