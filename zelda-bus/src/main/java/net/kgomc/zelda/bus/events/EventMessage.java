package net.kgomc.zelda.bus.events;

import java.time.Instant;

/**
 * A received event, wrapping the typed payload with routing metadata.
 *
 * <pre>{@code
 * bus.on(EconomyEvents.COINS_UPDATED)
 *    .filter(msg -> !msg.fromServer().equals(bus.getServerId())) // ignore own events
 *    .map(EventMessage::payload)
 *    .subscribe(event -> { ... });
 * }</pre>
 *
 * @param <T> the event payload type
 */
public record EventMessage<T>(
        T       payload,
        String  fromServer,
        String  topic,
        Instant timestamp
) {
    /** Convenience accessor — same as {@code payload()}. */
    public T get() { return payload; }

    /** True if this event originated from the given server. */
    public boolean isFrom(String serverId) {
        return fromServer != null && fromServer.equals(serverId);
    }
}