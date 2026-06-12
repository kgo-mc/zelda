package net.kgomc.zelda.bus.events;

import net.kgomc.zelda.bus.transport.BusTransport;
import net.kgomc.zelda.core.serialization.ZeldaGson;

import java.time.Instant;

/**
 * Fluent builder for publishing events with optional routing.
 * Always end the chain with {@link #send()} — nothing is published until then.
 *
 * <pre>{@code
 * // Broadcast — all servers receive it
 * bus.publish(EconomyEvents.COINS_UPDATED, payload)
 *    .send();
 *
 * // Targeted — only "dungeon-3" receives it
 * bus.publish(DungeonEvents.INSTANCE_READY, payload)
 *    .to("dungeon-3")
 *    .send();
 *
 * // Explicit broadcast (self-documenting)
 * bus.publish(DungeonEvents.ROUND_START, payload)
 *    .toAll()
 *    .send();
 * }</pre>
 */
public final class PublishBuilder<T> {

    private final ZeldaEventDef<T> def;
    private final T                payload;
    private final BusTransport     transport;
    private final String           serverId;

    private String targetServerId = null; // null = broadcast to all

    PublishBuilder(ZeldaEventDef<T> def, T payload,
                   BusTransport transport, String serverId) {
        this.def       = def;
        this.payload   = payload;
        this.transport = transport;
        this.serverId  = serverId;
    }

    /**
     * Routes this event to a specific server only.
     *
     * @param targetServerId the server ID to route to
     */
    public PublishBuilder<T> to(String targetServerId) {
        this.targetServerId = targetServerId;
        return this;
    }

    /**
     * Explicitly marks this as a broadcast to all servers (default behaviour,
     * but calling this makes intent clear in the code).
     */
    public PublishBuilder<T> toAll() {
        this.targetServerId = null;
        return this;
    }

    /**
     * Publishes the event. Must be called to actually send anything —
     * nothing is dispatched until this method is invoked.
     */
    public void send() {
        String subject = targetServerId != null
                ? def.getTopic() + "." + targetServerId
                : def.getTopic();

        WireEnvelope envelope = new WireEnvelope(
                serverId,
                def.getTopic(),
                Instant.now().toEpochMilli(),
                ZeldaGson.toJson(payload)
        );

        byte[] bytes = ZeldaGson.toJson(envelope)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);

        transport.publish(subject, bytes);
    }

    /** Internal wire format — wraps payload with routing metadata. */
    record WireEnvelope(String fromServer, String topic, long timestamp, String payload) {}
}