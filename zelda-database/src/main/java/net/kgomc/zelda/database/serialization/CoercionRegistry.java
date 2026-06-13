package net.kgomc.zelda.database.serialization;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of JDBC → Java type coercions used by {@link ResultSerializer#toObject}.
 *
 * <p>All standard types are pre-registered. You can override any of them or add
 * support for custom types (enums, value objects, etc.) via {@link #register}.</p>
 *
 * <h2>Registering a custom coercion</h2>
 * <pre>{@code
 * // Enum stored as VARCHAR
 * CoercionRegistry.global().register(GameMode.class,
 *     (rs, col) -> GameMode.valueOf(rs.getString(col)));
 *
 * // Value object wrapping a long
 * CoercionRegistry.global().register(PlayerId.class,
 *     (rs, col) -> new PlayerId(rs.getLong(col)));
 * }</pre>
 *
 * <h2>Using a scoped registry (e.g. per-module, for testing)</h2>
 * <pre>{@code
 * CoercionRegistry registry = CoercionRegistry.withDefaults();
 * registry.register(Rank.class, (rs, col) -> Rank.fromDb(rs.getString(col)));
 *
 * RowMapper<Player> mapper = ResultSerializer.toObject(Player.class, registry);
 * }</pre>
 */
public final class CoercionRegistry {

    // ------------------------------------------------------------------
    // Functional interface
    // ------------------------------------------------------------------

    /**
     * Extracts and converts a single column value from a {@link ResultSet}.
     *
     * @param <T> the Java type produced
     */
    @FunctionalInterface
    public interface Coercer<T> {
        T coerce(ResultSet rs, int columnIndex) throws Exception;
    }

    // ------------------------------------------------------------------
    // Singleton global registry
    // ------------------------------------------------------------------

    private static final CoercionRegistry GLOBAL = withDefaults();

    /** Returns the shared global registry. Safe to mutate at startup. */
    public static CoercionRegistry global() {
        return GLOBAL;
    }

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------

    private final Map<Class<?>, Coercer<?>> coercers = new ConcurrentHashMap<>();

    private CoercionRegistry() {}

    // ------------------------------------------------------------------
    // Factory
    // ------------------------------------------------------------------

    /**
     * Creates a fresh registry pre-populated with all built-in coercions.
     * Use this when you want module-scoped or test-scoped registries that
     * won't affect the global one.
     */
    public static CoercionRegistry withDefaults() {
        CoercionRegistry r = new CoercionRegistry();
        r.registerDefaults();
        return r;
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Registers (or replaces) a coercion for the given Java type.
     *
     * @param type    the target Java class
     * @param coercer how to extract a value of that type from a {@link ResultSet}
     * @param <T>     the Java type
     * @return {@code this}, for fluent chaining
     */
    public <T> CoercionRegistry register(Class<T> type, Coercer<T> coercer) {
        coercers.put(type, coercer);
        return this;
    }

    /**
     * Looks up the coercer for the given type.
     *
     * <p>Falls back to {@code rs.getObject(col, type)} if no explicit coercer
     * is registered — useful for driver-native types the JDBC driver can handle
     * directly.</p>
     */
    @SuppressWarnings("unchecked")
    public <T> T coerce(ResultSet rs, int columnIndex, Class<T> type) throws Exception {
        Coercer<?> coercer = coercers.get(type);
        if (coercer != null) {
            return (T) coercer.coerce(rs, columnIndex);
        }

        // Enum fallback — stored as VARCHAR
        if (type.isEnum()) {
            String raw = rs.getString(columnIndex);
            return raw != null ? (T) Enum.valueOf((Class<Enum>) type, raw) : null;
        }

        // Driver-native fallback (handles driver-specific types, PGobject, etc.)
        return rs.getObject(columnIndex, type);
    }

    /**
     * Returns {@code true} if an explicit coercer is registered for this type.
     * Useful for diagnostics or conditional logic.
     */
    public boolean isRegistered(Class<?> type) {
        return coercers.containsKey(type);
    }

    // ------------------------------------------------------------------
    // Built-in defaults
    // ------------------------------------------------------------------

    private void registerDefaults() {
        // --- Primitives & boxed ---
        register(String.class, ResultSet::getString);
        register(int.class, ResultSet::getInt);
        register(Integer.class,    (rs, col) -> { int v = rs.getInt(col); return rs.wasNull() ? null : v; });
        register(long.class, ResultSet::getLong);
        register(Long.class,       (rs, col) -> { long v = rs.getLong(col); return rs.wasNull() ? null : v; });
        register(double.class, ResultSet::getDouble);
        register(Double.class,     (rs, col) -> { double v = rs.getDouble(col); return rs.wasNull() ? null : v; });
        register(float.class, ResultSet::getFloat);
        register(Float.class,      (rs, col) -> { float v = rs.getFloat(col); return rs.wasNull() ? null : v; });
        register(boolean.class, ResultSet::getBoolean);
        register(Boolean.class,    (rs, col) -> { boolean v = rs.getBoolean(col); return rs.wasNull() ? null : v; });
        register(short.class, ResultSet::getShort);
        register(Short.class,      (rs, col) -> { short v = rs.getShort(col); return rs.wasNull() ? null : v; });
        register(byte.class, ResultSet::getByte);
        register(Byte.class,       (rs, col) -> { byte v = rs.getByte(col); return rs.wasNull() ? null : v; });
        register(byte[].class, ResultSet::getBytes);

        // --- Precision types ---
        register(BigDecimal.class, ResultSet::getBigDecimal);
        register(BigInteger.class, (rs, col) -> {
            BigDecimal bd = rs.getBigDecimal(col);
            return bd != null ? bd.toBigIntegerExact() : null;
        });

        // --- Date/time (java.time) ---
        register(Instant.class, (rs, col) -> {
            Timestamp ts = rs.getTimestamp(col);
            return ts != null ? ts.toInstant() : null;
        });
        register(LocalDate.class, (rs, col) -> {
            Date d = rs.getDate(col);
            return d != null ? d.toLocalDate() : null;
        });
        register(LocalDateTime.class, (rs, col) -> {
            Timestamp ts = rs.getTimestamp(col);
            return ts != null ? ts.toLocalDateTime() : null;
        });

        // --- Common JVM types ---
        register(UUID.class, (rs, col) -> {
            String raw = rs.getString(col);
            return raw != null ? UUID.fromString(raw) : null;
        });
    }
}
