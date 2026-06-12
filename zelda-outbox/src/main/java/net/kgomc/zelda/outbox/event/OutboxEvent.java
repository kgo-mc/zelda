package net.kgomc.zelda.outbox.event;

import com.google.gson.JsonObject;
import net.kgomc.zelda.core.serialization.ZeldaGson;

import java.time.Instant;
import java.util.UUID;

/**
 * An event read from the {@code zelda_outbox} table.
 *
 * <p>The payload is a JSON object — use the typed accessors ({@link #getString},
 * {@link #getInt}, etc.) rather than parsing manually.</p>
 *
 * <pre>{@code
 * outbox.observe("player.reward")
 *     .subscribe(event -> {
 *         UUID uuid   = event.getUUID("uuid");
 *         int  coins  = event.getInt("coins");
 *         economy.addCoins(uuid, coins);
 *     });
 * }</pre>
 */
public final class OutboxEvent {

    private final UUID       id;
    private final String     eventType;
    private final JsonObject payload;
    private final int        attempts;
    private final Instant    createdAt;
    private final Instant    processAt;

    public OutboxEvent(UUID id, String eventType, String payloadJson,
                       int attempts, Instant createdAt, Instant processAt) {
        this.id        = id;
        this.eventType = eventType;
        this.payload   = ZeldaGson.fromJson(payloadJson, JsonObject.class);
        this.attempts  = attempts;
        this.createdAt = createdAt;
        this.processAt = processAt;
    }

    // -----------------------------------------------------------------------
    // Identity
    // -----------------------------------------------------------------------

    public UUID    getId()        { return id; }
    public String  getEventType() { return eventType; }
    public int     getAttempts()  { return attempts; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getProcessAt() { return processAt; }

    // -----------------------------------------------------------------------
    // Payload accessors
    // -----------------------------------------------------------------------

    public String getString(String key) {
        return payload.has(key) ? payload.get(key).getAsString() : null;
    }

    public int getInt(String key) {
        return payload.has(key) ? payload.get(key).getAsInt() : 0;
    }

    public long getLong(String key) {
        return payload.has(key) ? payload.get(key).getAsLong() : 0L;
    }

    public double getDouble(String key) {
        return payload.has(key) ? payload.get(key).getAsDouble() : 0.0;
    }

    public boolean getBoolean(String key) {
        return payload.has(key) && payload.get(key).getAsBoolean();
    }

    public UUID getUUID(String key) {
        String raw = getString(key);
        return raw != null ? UUID.fromString(raw) : null;
    }

    /**
     * Deserializes a nested JSON object field into a POJO via {@link ZeldaGson}.
     *
     * <pre>{@code
     * // Published with a nested object
     * outbox.publish(conn, "player.purchase", Map.of(
     *     "uuid", uuid.toString(),
     *     "item", Map.of("id", "diamond_sword", "count", 1)
     * ));
     *
     * // Retrieved in handler
     * ItemDetails item = event.getObject("item", ItemDetails.class);
     * }</pre>
     *
     * @param key  the payload field name
     * @param type the target class to deserialize into
     * @return deserialized object, or {@code null} if the key is absent
     */
    public <T> T getObject(String key, Class<T> type) {
        if (!payload.has(key) || payload.get(key).isJsonNull()) return null;
        return ZeldaGson.fromJson(payload.get(key), type);
    }

    /** Returns the raw JSON payload string. */
    public String getRawPayload() {
        return ZeldaGson.toJson(payload);
    }

    @Override
    public String toString() {
        return "OutboxEvent{id=" + id + ", type=" + eventType + ", attempts=" + attempts + "}";
    }
}