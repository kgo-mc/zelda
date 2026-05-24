package net.kgomc.zelda.messaging.channel;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.title.Title;

import java.time.Duration;

/**
 * Payload for {@link SendChannel#TITLE} — title + optional subtitle + timing.
 *
 * <pre>{@code
 * TitleData.of("<primary>Round Start!</primary>", "<muted>Get ready...</muted>")
 * TitleData.of("<error>Game Over</error>", null, Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))
 * }</pre>
 */
public record TitleData(
        String title,
        String subtitle,          // nullable
        Duration fadeIn,
        Duration stay,
        Duration fadeOut
) {
    private static final Duration DEFAULT_FADE = Duration.ofMillis(500);
    private static final Duration DEFAULT_STAY = Duration.ofSeconds(3);

    public static TitleData of(String title, String subtitle) {
        return new TitleData(title, subtitle, DEFAULT_FADE, DEFAULT_STAY, DEFAULT_FADE);
    }

    public static TitleData of(String title, String subtitle,
                               Duration fadeIn, Duration stay, Duration fadeOut) {
        return new TitleData(title, subtitle, fadeIn, stay, fadeOut);
    }
}