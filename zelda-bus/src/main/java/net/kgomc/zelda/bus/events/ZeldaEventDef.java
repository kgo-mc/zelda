package net.kgomc.zelda.bus.events;

/**
 * Type-safe event definition. Plugin authors declare these as public static
 * constants in their API artifact so consumers get compile-time safety.
 *
 * <h2>Declaring events (in your plugin's API module)</h2>
 * <pre>{@code
 * public final class EconomyEvents {
 *
 *     public static final ZeldaEventDef<CoinsUpdatedEvent> COINS_UPDATED =
 *         ZeldaEventDef.of("economy.coins_updated", CoinsUpdatedEvent.class);
 *
 *     public static final ZeldaEventDef<PlayerBankruptEvent> PLAYER_BANKRUPT =
 *         ZeldaEventDef.of("economy.player_bankrupt", PlayerBankruptEvent.class);
 * }
 * }</pre>
 *
 * <h2>Publishing (in your plugin implementation)</h2>
 * <pre>{@code
 * bus.publish(EconomyEvents.COINS_UPDATED,
 *     new CoinsUpdatedEvent(uuid, oldCoins, newCoins, "purchase"));
 *
 * // Targeted — only server "lobby-1" receives it
 * bus.publish(EconomyEvents.COINS_UPDATED, payload).to("lobby-1");
 *
 * // Broadcast — ALL servers receive it (no queue group)
 * bus.publish(EconomyEvents.SERVER_RESTART, payload).toAll();
 * }</pre>
 *
 * <h2>Subscribing (in any plugin that depends on the API)</h2>
 * <pre>{@code
 * bus.on(EconomyEvents.COINS_UPDATED)           // Observable<CoinsUpdatedEvent>
 *    .filter(e -> e.newCoins() > e.oldCoins())
 *    .observeOn(ZeldaSchedulers.serverThread())
 *    .subscribe(e -> scoreboard.update(e.uuid(), e.newCoins()));
 * }</pre>
 *
 * @param <T> the event payload type
 */
public final class ZeldaEventDef<T> {

    private final String   topic;
    private final Class<T> payloadType;

    private ZeldaEventDef(String topic, Class<T> payloadType) {
        this.topic       = topic;
        this.payloadType = payloadType;
    }

    public static <T> ZeldaEventDef<T> of(String topic, Class<T> payloadType) {
        if (topic == null || topic.isBlank())
            throw new IllegalArgumentException("topic must not be blank");
        if (payloadType == null)
            throw new IllegalArgumentException("payloadType must not be null");
        return new ZeldaEventDef<>(topic, payloadType);
    }

    public String   getTopic()       { return topic; }
    public Class<T> getPayloadType() { return payloadType; }

    @Override
    public String toString() {
        return "ZeldaEventDef{topic='" + topic + "', type=" + payloadType.getSimpleName() + "}";
    }
}