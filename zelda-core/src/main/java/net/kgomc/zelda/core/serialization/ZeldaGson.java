package net.kgomc.zelda.core.serialization;

import com.google.gson.*;
import net.kgomc.zelda.core.context.RuntimeKind;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.UUID;

/**
 * Shared, pre-configured {@link Gson} instance for the Zelda library.
 *
 * <p>Must be initialised once before any module uses it by calling
 * {@link #initialize(RuntimeKind)} from {@code ZeldaBuilder} — this runs
 * all reflection upfront at startup rather than per-serialization call.</p>
 *
 * <p>After initialisation every serialization call is a plain volatile read
 * with zero reflection overhead.</p>
 *
 * <h2>Included serializers</h2>
 * <ul>
 *   <li>{@link UUID} — plain string, always registered</li>
 *   <li>{@code org.bukkit.Location} — {@code {world,x,y,z,yaw,pitch}} (Paper only)</li>
 *   <li>{@code org.bukkit.inventory.ItemStack} — via Bukkit's own serialize map (Paper only)</li>
 *   <li>{@code org.bukkit.util.Vector} — {@code {x,y,z}} (Paper only)</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // From anywhere after Zelda.builder().initialize() completes
 * String json = ZeldaGson.toJson(myObject);
 * MyObject obj = ZeldaGson.fromJson(json, MyObject.class);
 * }</pre>
 */
public final class ZeldaGson {

    private static volatile Gson INSTANCE;

    private ZeldaGson() {}

    // -----------------------------------------------------------------------
    // Initialisation — called once by ZeldaBuilder before enableAll()
    // -----------------------------------------------------------------------

    /**
     * Holds a custom adapter entry passed from ZeldaBuilder.
     */
    public record AdapterEntry(Class<?> type, Object adapter, boolean hierarchy) {}

    /**
     * Builds and caches the Gson instance.
     * All reflection for platform-specific serializers runs here — once.
     *
     * @param kind    the runtime platform — determines which serializers are registered
     * @param custom  additional adapters registered by the plugin via ZeldaBuilder
     * @throws IllegalStateException if called more than once
     */
    public static synchronized void initialize(RuntimeKind kind, java.util.List<AdapterEntry> custom) {
        if (INSTANCE != null) {
            throw new IllegalStateException(
                    "ZeldaGson is already initialised. initialize() must be called exactly once.");
        }
        INSTANCE = build(kind, custom);
    }

    /** Convenience overload with no custom adapters. */
    public static synchronized void initialize(RuntimeKind kind) {
        initialize(kind, java.util.List.of());
    }

    /**
     * Resets the instance — called by {@code Zelda.shutdown()} and in tests.
     */
    public static synchronized void reset() {
        INSTANCE = null;
    }

    // -----------------------------------------------------------------------
    // Public API — no Gson type in any signature
    // -----------------------------------------------------------------------

    /**
     * Serializes {@code obj} to its JSON representation.
     *
     * @throws IllegalStateException if {@link #initialize(RuntimeKind)} has not been called
     */
    public static String toJson(Object obj) {
        return instance().toJson(obj);
    }

    /**
     * Serializes {@code obj} to JSON using the given {@link Type} — useful for generics.
     *
     * @throws IllegalStateException if {@link #initialize(RuntimeKind)} has not been called
     */
    public static String toJson(Object obj, Type type) {
        return instance().toJson(obj, type);
    }

    /**
     * Deserializes {@code json} into an object of type {@code T}.
     *
     * @throws IllegalStateException if {@link #initialize(RuntimeKind)} has not been called
     */
    public static <T> T fromJson(String json, Class<T> type) {
        return instance().fromJson(json, type);
    }

    /**
     * Deserializes {@code json} into an object of the given {@link Type} — useful for generics.
     *
     * @throws IllegalStateException if {@link #initialize(RuntimeKind)} has not been called
     */
    public static <T> T fromJson(String json, Type type) {
        return instance().fromJson(json, type);
    }

    /**
     * Deserializes a {@link JsonElement} into an object of type {@code T}.
     *
     * @throws IllegalStateException if {@link #initialize(RuntimeKind)} has not been called
     */
    public static <T> T fromJson(JsonElement element, Class<T> type) {
        return instance().fromJson(element, type);
    }

    public static void toJson(Object obj, Appendable writer) throws IOException {
        instance().toJson(obj, writer);
    }

    public static <T> T fromJson(Reader reader, Class<T> type) {
        return instance().fromJson(reader, type);
    }

    // -----------------------------------------------------------------------
    // Internal accessor — Gson type stays inside this module
    // -----------------------------------------------------------------------

    static Gson instance() {
        Gson instance = INSTANCE;
        if (instance == null) {
            throw new IllegalStateException(
                    "ZeldaGson has not been initialised. " +
                            "This should not happen — ZeldaBuilder initialises it before modules start.");
        }
        return instance;
    }

    // -----------------------------------------------------------------------
    // Builder — runs once at startup
    // -----------------------------------------------------------------------

    private static Gson build(RuntimeKind kind, java.util.List<AdapterEntry> custom) {
        GsonBuilder builder = new GsonBuilder()
                .serializeNulls()
                .disableHtmlEscaping()
                .setPrettyPrinting();

        // Always available
        builder.registerTypeAdapter(UUID.class, new UUIDAdapter());

        // Paper-specific — only on SERVER, reflection runs once here
        if (kind == RuntimeKind.SERVER) {
            tryRegisterLocation(builder);
            tryRegisterItemStack(builder);
            tryRegisterVector(builder);
        }

        // Plugin-supplied custom adapters — applied last so they can override defaults
        for (AdapterEntry entry : custom) {
            if (entry.hierarchy()) {
                builder.registerTypeHierarchyAdapter(entry.type(), entry.adapter());
            } else {
                builder.registerTypeAdapter(entry.type(), entry.adapter());
            }
        }

        return builder.create();
    }

    // -----------------------------------------------------------------------
    // Core adapters
    // -----------------------------------------------------------------------

    private static final class UUIDAdapter
            implements JsonSerializer<UUID>, JsonDeserializer<UUID> {

        @Override
        public JsonElement serialize(UUID src, Type type, JsonSerializationContext ctx) {
            return new JsonPrimitive(src.toString());
        }

        @Override
        public UUID deserialize(JsonElement json, Type type, JsonDeserializationContext ctx)
                throws JsonParseException {
            return UUID.fromString(json.getAsString());
        }
    }

    // -----------------------------------------------------------------------
    // Paper adapters — reflection runs once here at build time
    // -----------------------------------------------------------------------

    private static void tryRegisterLocation(GsonBuilder builder) {
        try {
            Class<?> locationClass  = Class.forName("org.bukkit.Location");
            Class<?> worldClass     = Class.forName("org.bukkit.World");
            Class<?> bukkitClass    = Class.forName("org.bukkit.Bukkit");

            java.lang.reflect.Method getWorld  = locationClass.getMethod("getWorld");
            java.lang.reflect.Method worldName = worldClass.getMethod("getName");
            java.lang.reflect.Method getX      = locationClass.getMethod("getX");
            java.lang.reflect.Method getY      = locationClass.getMethod("getY");
            java.lang.reflect.Method getZ      = locationClass.getMethod("getZ");
            java.lang.reflect.Method getYaw    = locationClass.getMethod("getYaw");
            java.lang.reflect.Method getPitch  = locationClass.getMethod("getPitch");
            java.lang.reflect.Method getWorldM = bukkitClass.getMethod("getWorld", String.class);
            java.lang.reflect.Constructor<?> ctor = locationClass.getConstructor(
                    worldClass, double.class, double.class, double.class, float.class, float.class);

            builder.registerTypeHierarchyAdapter(locationClass,
                    new JsonSerializer<Object>() {
                        @Override
                        public JsonElement serialize(Object src, Type t, JsonSerializationContext ctx) {
                            try {
                                Object world = getWorld.invoke(src);
                                JsonObject obj = new JsonObject();
                                obj.addProperty("world", (String) worldName.invoke(world));
                                obj.addProperty("x",     (double) getX.invoke(src));
                                obj.addProperty("y",     (double) getY.invoke(src));
                                obj.addProperty("z",     (double) getZ.invoke(src));
                                obj.addProperty("yaw",   (float)  getYaw.invoke(src));
                                obj.addProperty("pitch", (float)  getPitch.invoke(src));
                                return obj;
                            } catch (Exception e) { return JsonNull.INSTANCE; }
                        }
                    });

            builder.registerTypeHierarchyAdapter(locationClass,
                    new JsonDeserializer<Object>() {
                        @Override
                        public Object deserialize(JsonElement json, Type t, JsonDeserializationContext ctx)
                                throws JsonParseException {
                            try {
                                JsonObject obj = json.getAsJsonObject();
                                Object world = getWorldM.invoke(null, obj.get("world").getAsString());
                                return ctor.newInstance(
                                        world,
                                        obj.get("x").getAsDouble(),
                                        obj.get("y").getAsDouble(),
                                        obj.get("z").getAsDouble(),
                                        obj.get("yaw").getAsFloat(),
                                        obj.get("pitch").getAsFloat()
                                );
                            } catch (Exception e) {
                                throw new JsonParseException("Failed to deserialize Location", e);
                            }
                        }
                    });
        } catch (Exception ignored) {}
    }

    private static void tryRegisterItemStack(GsonBuilder builder) {
        try {
            Class<?> itemClass   = Class.forName("org.bukkit.inventory.ItemStack");
            java.lang.reflect.Method serialize   = itemClass.getMethod("serialize");
            java.lang.reflect.Method deserialize = itemClass.getMethod("deserialize", java.util.Map.class);
            com.google.gson.reflect.TypeToken<?> mapToken =
                    new com.google.gson.reflect.TypeToken<java.util.Map<String, Object>>(){};

            builder.registerTypeHierarchyAdapter(itemClass,
                    new JsonSerializer<Object>() {
                        @Override
                        public JsonElement serialize(Object src, Type t, JsonSerializationContext ctx) {
                            try {
                                return ctx.serialize(serialize.invoke(src));
                            } catch (Exception e) { return JsonNull.INSTANCE; }
                        }
                    });

            builder.registerTypeHierarchyAdapter(itemClass,
                    new JsonDeserializer<Object>() {
                        @Override
                        public Object deserialize(JsonElement json, Type t, JsonDeserializationContext ctx)
                                throws JsonParseException {
                            try {
                                java.util.Map<String, Object> map = ctx.deserialize(json, mapToken.getType());
                                return deserialize.invoke(null, map);
                            } catch (Exception e) {
                                throw new JsonParseException("Failed to deserialize ItemStack", e);
                            }
                        }
                    });
        } catch (Exception ignored) {}
    }

    private static void tryRegisterVector(GsonBuilder builder) {
        try {
            Class<?> vectorClass = Class.forName("org.bukkit.util.Vector");
            java.lang.reflect.Method getX = vectorClass.getMethod("getX");
            java.lang.reflect.Method getY = vectorClass.getMethod("getY");
            java.lang.reflect.Method getZ = vectorClass.getMethod("getZ");
            java.lang.reflect.Constructor<?> ctor =
                    vectorClass.getConstructor(double.class, double.class, double.class);

            builder.registerTypeHierarchyAdapter(vectorClass,
                    new JsonSerializer<Object>() {
                        @Override
                        public JsonElement serialize(Object src, Type t, JsonSerializationContext ctx) {
                            try {
                                JsonObject obj = new JsonObject();
                                obj.addProperty("x", (double) getX.invoke(src));
                                obj.addProperty("y", (double) getY.invoke(src));
                                obj.addProperty("z", (double) getZ.invoke(src));
                                return obj;
                            } catch (Exception e) { return JsonNull.INSTANCE; }
                        }
                    });

            builder.registerTypeHierarchyAdapter(vectorClass,
                    new JsonDeserializer<Object>() {
                        @Override
                        public Object deserialize(JsonElement json, Type t, JsonDeserializationContext ctx)
                                throws JsonParseException {
                            try {
                                JsonObject obj = json.getAsJsonObject();
                                return ctor.newInstance(
                                        obj.get("x").getAsDouble(),
                                        obj.get("y").getAsDouble(),
                                        obj.get("z").getAsDouble()
                                );
                            } catch (Exception e) {
                                throw new JsonParseException("Failed to deserialize Vector", e);
                            }
                        }
                    });
        } catch (Exception ignored) {}
    }
}