package net.kgomc.zelda.bus.events;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import net.kgomc.zelda.bus.transport.BusTransport;
import net.kgomc.zelda.core.reactive.ZeldaSchedulers;
import net.kgomc.zelda.core.serialization.ZeldaGson;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Type-safe event bus over NATS.
 *
 * <h2>Publishing</h2>
 * <pre>{@code
 * // Broadcast — all servers receive it
 * bus.publish(EconomyEvents.COINS_UPDATED,
 *     new CoinsUpdatedEvent(uuid, 0, 100, "reward"));
 *
 * // Targeted — only "lobby-1" receives it
 * bus.publish(DungeonEvents.INSTANCE_READY, payload).to("lobby-1");
 * }</pre>
 *
 * <h2>Subscribing</h2>
 * <pre>{@code
 * bus.on(EconomyEvents.COINS_UPDATED)              // Observable<EventMessage<CoinsUpdatedEvent>>
 *    .map(EventMessage::payload)                   // unwrap to CoinsUpdatedEvent
 *    .filter(e -> e.newCoins() > e.oldCoins())
 *    .observeOn(ZeldaSchedulers.serverThread())
 *    .subscribe(e -> scoreboard.update(e.uuid(), e.newCoins()));
 * }</pre>
 */
public final class ZeldaEventBus {

    private final BusTransport transport;
    private final String       serverId;
    private final Logger       logger;

    /** Cached Observables per topic — shared across all subscribers to the same topic */
    private final Map<String, Observable<?>> topicObservables = new ConcurrentHashMap<>();

    private final CompositeDisposable disposables = new CompositeDisposable();

    public ZeldaEventBus(BusTransport transport, String serverId, Logger logger) {
        this.transport = transport;
        this.serverId  = serverId;
        this.logger    = logger;
    }

    // -----------------------------------------------------------------------
    // Publish
    // -----------------------------------------------------------------------

    /**
     * Publishes an event. Returns a {@link PublishBuilder} for optional routing.
     *
     * <p>Default behaviour (no chained call) is broadcast to all servers.
     * Chain {@code .to("server-id")} for targeted delivery.</p>
     */
    public <T> PublishBuilder<T> publish(ZeldaEventDef<T> def, T payload) {
        return new PublishBuilder<>(def, payload, transport, serverId);
    }

    // -----------------------------------------------------------------------
    // Subscribe
    // -----------------------------------------------------------------------

    /**
     * Returns an Observable that emits every received event of the given type.
     *
     * <p>Subscribes to both the base topic (broadcasts) and the server-targeted
     * subject ({@code topic.serverId}) so targeted events are also received.</p>
     *
     * <p>The Observable is shared — multiple subscribers to the same def
     * share one NATS subscription.</p>
     */
    @SuppressWarnings("unchecked")
    public <T> Observable<EventMessage<T>> on(ZeldaEventDef<T> def) {
        return (Observable<EventMessage<T>>) topicObservables.computeIfAbsent(
                def.getTopic(), topic -> buildObservable(def)
        );
    }

    public void dispose() {
        disposables.dispose();
        topicObservables.clear();
    }

    // -----------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------

    private <T> Observable<EventMessage<T>> buildObservable(ZeldaEventDef<T> def) {
        // Subscribe to TWO NATS subjects and merge them into one Observable:
        //
        // 1. "economy.coins_updated"          ← broadcasts (all servers receive this)
        // 2. "economy.coins_updated.lobby-1"  ← targeted (only THIS server receives this,
        //                                        because only this server subscribes to it)
        //
        // Callers get one Observable — they don't need to know which subject the
        // message arrived on. Use EventMessage.fromServer() if you need to know origin.
        Observable<EventMessage<T>> broadcast = transport
                .subscribe(def.getTopic())
                .map(msg -> decode(msg, def));

        Observable<EventMessage<T>> targeted = transport
                .subscribe(def.getTopic() + "." + serverId)
                .map(msg -> decode(msg, def));

        logger.info("[Zelda/Bus] Subscribed to " + def.getTopic()+" and "+def.getTopic()+"."+serverId+".");
        return Observable.merge(broadcast, targeted)
                .subscribeOn(ZeldaSchedulers.io())
                .share();
    }

    private <T> EventMessage<T> decode(
            net.kgomc.zelda.bus.transport.TransportMessage msg,
            ZeldaEventDef<T> def
    ) {
        PublishBuilder.WireEnvelope envelope = ZeldaGson
                .fromJson(msg.getDataAsString(), PublishBuilder.WireEnvelope.class);

        T payload = ZeldaGson.fromJson(envelope.payload(), def.getPayloadType());

        return new EventMessage<>(
                payload,
                envelope.fromServer(),
                envelope.topic(),
                Instant.ofEpochMilli(envelope.timestamp())
        );
    }
}