package net.kgomc.zelda.ui.context;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A typed key-value bag scoped to a single open menu instance.
 *
 * <p>Thread-safe via {@link ConcurrentHashMap} — safe to read and write
 * from async click handlers or scheduled tasks without external locking.</p>
 *
 * <pre>{@code
 * // Store on first click
 * ctx.state().put("selected_page", 2);
 *
 * // Read in showIf
 * int page = ctx.state().get("selected_page", Integer.class).orElse(0);
 * }</pre>
 */
public final class UIStateBag {

    private final ConcurrentHashMap<String, Object> data = new ConcurrentHashMap<>();

    /**
     * Stores a value. Neither key nor value may be null.
     */
    public void put(String key, @NotNull Object value) {
        if (key == null)   throw new IllegalArgumentException("key must not be null");
        if (value == null) throw new IllegalArgumentException("value must not be null — use remove() to clear a key");
        data.put(key, value);
    }

    /**
     * Returns the value for the key cast to the given type, or empty if
     * absent or the stored value is not an instance of the requested type.
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String key, Class<T> type) {
        Object value = data.get(key);
        if (value == null || !type.isInstance(value)) return Optional.empty();
        return Optional.of((T) value);
    }

    /** Returns true if the key is present. */
    public boolean has(String key) {
        return data.containsKey(key);
    }

    /** Removes the key if present. */
    public void remove(String key) {
        data.remove(key);
    }

    /** Atomically puts the value only if the key is not already present. Returns true if stored. */
    public boolean putIfAbsent(String key, Object value) {
        if (key == null)   throw new IllegalArgumentException("key must not be null");
        if (value == null) throw new IllegalArgumentException("value must not be null");
        return data.putIfAbsent(key, value) == null;
    }

    /** Clears all entries. */
    public void clear() {
        data.clear();
    }
}