package net.kgomc.zelda.database.serialization;

import com.google.gson.Gson;
import net.kgomc.zelda.core.serialization.ZeldaGson;
import net.kgomc.zelda.database.query.RowMapper;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Factory for common {@link RowMapper} implementations.
 *
 * <h2>Usage examples</h2>
 * <pre>{@code
 * // Map to a plain Map<String, Object> (good for dynamic/unknown schemas)
 * List<Map<String, Object>> rows = runner.query(sql, ResultSerializer.toMap());
 *
 * // Map to a POJO via Gson (field names must match column names)
 * List<PlayerData> players = runner.query(sql, ResultSerializer.toObject(PlayerData.class));
 *
 * // Map a single UUID column
 * Optional<UUID> id = runner.queryOne(sql, ResultSerializer.toUUID("uuid"), playerId);
 * }</pre>
 */
public final class ResultSerializer {

    private ResultSerializer() {}

    // -----------------------------------------------------------------------
    // Map mapper
    // -----------------------------------------------------------------------

    /**
     * Maps each row to a {@code Map<String, Object>} keyed by column name.
     * Useful for dynamic queries where the schema isn't known at compile time.
     */
    public static RowMapper<Map<String, Object>> toMap() {
        return rs -> {
            ResultSetMetaData meta = rs.getMetaData();
            int cols = meta.getColumnCount();
            Map<String, Object> row = new LinkedHashMap<>(cols);
            for (int i = 1; i <= cols; i++) {
                row.put(meta.getColumnLabel(i), rs.getObject(i));
            }
            return row;
        };
    }

    // -----------------------------------------------------------------------
    // POJO mapper via Gson
    // -----------------------------------------------------------------------

    /**
     * Maps each row to a POJO of type {@code T} using the global {@link CoercionRegistry}.
     * Resolves fields by matching column labels (supports snake_case → camelCase).
     */
    public static <T> RowMapper<T> toObject(Class<T> type) {
        return toObject(type, CoercionRegistry.global(), MappingStrategy.STRICT);
    }

    public static <T> RowMapper<T> toObject(Class<T> type, MappingStrategy strategy) {
        return toObject(type, CoercionRegistry.global(), strategy);
    }

    public static <T> RowMapper<T> toObject(Class<T> type, CoercionRegistry registry) {
        Supplier<T> factory = () -> {
            try {
                Constructor<T> ctor = type.getDeclaredConstructor();
                ctor.setAccessible(true);
                return ctor.newInstance();
            } catch (NoSuchMethodException e) {
                throw new MappingException(
                        "No no-arg constructor found on " + type.getName() +
                                ". Use ResultSerializer.toObject(Supplier<T>) instead.", e);
            } catch (Exception e) {
                throw new MappingException(
                        "Failed to instantiate " + type.getName(), e);
            }
        };
        return toObject(factory, registry, MappingStrategy.STRICT);
    }

    /**
     * Maps each row to a POJO using a custom {@link CoercionRegistry}.
     * Use this for module-scoped or test-scoped coercions.
     */
    public static <T> RowMapper<T> toObject(Class<T> type, CoercionRegistry registry, MappingStrategy strategy) {
        Supplier<T> factory = () -> {
            try {
                Constructor<T> ctor = type.getDeclaredConstructor();
                ctor.setAccessible(true);
                return ctor.newInstance();
            } catch (NoSuchMethodException e) {
                throw new MappingException(
                        "No no-arg constructor found on " + type.getName() +
                                ". Use ResultSerializer.toObject(Supplier<T>) instead.", e);
            } catch (Exception e) {
                throw new MappingException(
                        "Failed to instantiate " + type.getName(), e);
            }
        };
        return toObject(factory, registry, strategy);
    }

    public static <T> RowMapper<T> toObject(Supplier<T> factory) {
        return toObject(factory, CoercionRegistry.global());
    }

    public static <T> RowMapper<T> toObject(Supplier<T> factory, MappingStrategy strategy) {
        return toObject(factory, CoercionRegistry.global(), strategy);
    }

    public static <T> RowMapper<T> toObject(Supplier<T> factory, CoercionRegistry registry) {
        return toObject(factory, registry, MappingStrategy.STRICT);
    }

    public static <T> RowMapper<T> toObject(Supplier<T> factory, CoercionRegistry registry, MappingStrategy strategy) {
        FieldCache cache = new FieldCache();
        return rs -> {
            ResultSetMetaData meta = rs.getMetaData();
            int cols = meta.getColumnCount();

            cache.resolveIfNeeded(factory, meta, cols, strategy);

            T instance = factory.get();
            for (int i = 1; i <= cols; i++) {
                Field field = cache.fields[i];
                if (field == null) {
                    continue;
                }
                try {
                    field.set(instance, registry.coerce(rs, i, field.getType()));
                } catch (Exception e) {
                    throw new MappingException("Failed to map column " + meta.getColumnLabel(i), e);
                }
            }
            return instance;
        };

    }

    // -----------------------------------------------------------------------
    // Scalar mappers
    // -----------------------------------------------------------------------

    /** Maps a single {@code String} column. */
    public static RowMapper<String> toString(String column) {
        return rs -> rs.getString(column);
    }

    /** Maps a single {@code int} column. */
    public static RowMapper<Integer> toInt(String column) {
        return rs -> rs.getInt(column);
    }

    /** Maps a single {@code long} column. */
    public static RowMapper<Long> toLong(String column) {
        return rs -> rs.getLong(column);
    }

    /** Maps a single {@code double} column. */
    public static RowMapper<Double> toDouble(String column) {
        return rs -> rs.getDouble(column);
    }

    /** Maps a single {@code boolean} column. */
    public static RowMapper<Boolean> toBoolean(String column) {
        return rs -> rs.getBoolean(column);
    }

    /**
     * Maps a {@code String} column to a {@link UUID}.
     * Assumes the UUID is stored as a standard string (e.g. {@code "550e8400-..."}).
     */
    public static RowMapper<UUID> toUUID(String column) {
        return rs -> {
            String raw = rs.getString(column);
            return raw != null ? UUID.fromString(raw) : null;
        };
    }

    // -----------------------------------------------------------------------
    // JSON column mapper
    // -----------------------------------------------------------------------

    /**
     * Deserialises a JSON-encoded column into a POJO via Gson.
     * Useful when you store complex objects as JSON strings in the DB.
     *
     * <pre>{@code
     * RowMapper<Inventory> mapper = ResultSerializer.fromJson("inventory_json", Inventory.class);
     * }</pre>
     */
    public static <T> RowMapper<T> fromJson(String column, Class<T> type) {
        return rs -> {
            String json = rs.getString(column);
            return json != null ? ZeldaGson.fromJson(json, type) : null;
        };
    }

    /**
     * Serialises an object to a JSON string — use when inserting/updating
     * JSON columns.
     *
     * <pre>{@code
     * runner.update("UPDATE players SET data = ? WHERE uuid = ?",
     *     ResultSerializer.toJson(playerData), uuid);
     * }</pre>
     */
    public static String toJson(Object obj) {
        return ZeldaGson.toJson(obj);
    }
}