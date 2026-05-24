package net.kgomc.zelda.messaging.target;

import net.kgomc.zelda.messaging.channel.*;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * {@link ITarget} wrapping a console {@link Audience}.
 *
 * <p>Works for both Paper ({@code Bukkit.getConsoleSender()}) and
 * Velocity ({@code ProxyServer.getConsoleCommandSource()}) since both
 * implement Adventure's {@link Audience}.</p>
 *
 * <pre>{@code
 * ITarget console = ConsoleTarget.of(Bukkit.getConsoleSender());       // Paper
 * ITarget console = ConsoleTarget.of(server.getConsoleCommandSource()); // Velocity
 * }</pre>
 */
public final class ConsoleTarget implements ITarget {

    private final Audience console;

    private ConsoleTarget(Audience console) {
        this.console = console;
    }

    public static ConsoleTarget of(Audience console) {
        if (console == null) throw new IllegalArgumentException("console must not be null");
        return new ConsoleTarget(console);
    }

    @Override public Optional<UUID> getUUID() { return Optional.empty(); }
    @Override public boolean isPlayer()        { return false; }

    @Override public void sendMessage(Component message)                            { console.sendMessage(message); }
    @Override public void sendActionBar(Component message)                          { /* no-op for console */ }
    @Override public void sendTitle(TitleData d, Component title, Component sub)    { /* no-op for console */ }
    @Override public void showBossBar(BossBarData d, Component name)                { /* no-op for console */ }
    @Override public void playSound(SoundData d)                                    { /* no-op for console */ }
}