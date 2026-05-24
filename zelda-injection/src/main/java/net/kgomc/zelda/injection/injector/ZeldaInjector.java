package net.kgomc.zelda.injection.injector;

import net.kgomc.zelda.core.context.ZeldaContext;
import net.kgomc.zelda.core.module.ZeldaModule;
import net.kgomc.zelda.injection.binder.ZeldaBinder;
import org.codejargon.feather.Feather;
import org.codejargon.feather.Key;

import javax.inject.Named;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Zelda's JSR-330 injector.
 *
 * <p>Uses a two-layer approach:</p>
 * <ul>
 *   <li><b>Instance registry</b> — pre-resolved instances (Zelda modules, plugin, logger, etc.)
 *       looked up directly by type before Feather is consulted.</li>
 *   <li><b>Feather</b> — handles constructor, field, and method injection for types
 *       not in the instance registry.</li>
 * </ul>
 *
 * <p>This avoids fighting Feather's {@code @Provides}-based module system for
 * dynamic bindings while still getting full JSR-330 injection for user classes.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * ZeldaInjector injector = (ZeldaInjector) ZeldaContext.get().getInjector();
 *
 * // Get any type — resolved from registry or via Feather constructor injection
 * EconomyService economy = injector.get(EconomyService.class);
 *
 * // Inject into an existing instance
 * injector.injectMembers(myCommandHandler);
 * }</pre>
 */
public final class ZeldaInjector {

    /** Pre-resolved instances keyed by type — checked before Feather */
    private final Map<Class<?>, Object> instanceRegistry;

    /** Supplier bindings — called on demand */
    private final Map<Class<?>, SupplierEntry<?>> supplierRegistry;

    /** Singleton cache for supplier bindings */
    private final ConcurrentHashMap<Class<?>, Object> singletonCache = new ConcurrentHashMap<>();

    /** Feather — handles constructor/field/method injection for unregistered types */
    private final Feather feather;

    private record SupplierEntry<T>(Supplier<T> supplier, boolean singleton) {}

    private ZeldaInjector(Map<Class<?>, Object> instances,
                          Map<Class<?>, SupplierEntry<?>> suppliers,
                          Feather feather) {
        this.instanceRegistry = instances;
        this.supplierRegistry = suppliers;
        this.feather          = feather;
    }

    // -----------------------------------------------------------------------
    // Factory
    // -----------------------------------------------------------------------

    /**
     * Builds the injector from user bindings + Zelda auto-bindings.
     * Must be called after all modules are enabled.
     */
    public static ZeldaInjector build(ZeldaBinder userBinder, ZeldaContext context) {
        Map<Class<?>, Object>            instances = new HashMap<>();
        Map<Class<?>, SupplierEntry<?>>  suppliers = new HashMap<>();

        // 1 — Auto-bind Zelda internals
        instances.put(ZeldaContext.class, context);
        instances.put(net.kgomc.zelda.core.context.ZeldaPlugin.class, context.getPlugin());
        instances.put(Logger.class, context.getLogger());
        instances.put(Path.class, context.getDataFolder());

        // 2 — Auto-bind every active module by its concrete class
        for (ZeldaModule module : context.getRegistry().getAll().values()) {
            instances.put(module.getClass(), module);
        }

        // 3 — User bindings (override auto-bindings if same type)
        if (userBinder != null) {
            for (ZeldaBinder.Binding<?> binding : userBinder.getBindings()) {
                switch (binding) {
                    case ZeldaBinder.Binding.InstanceBinding<?> b ->
                            instances.put(b.type(), b.instance());
                    case ZeldaBinder.Binding.SupplierBinding<?> b ->
                            suppliers.put(b.type(), new SupplierEntry<>(b.supplier(), b.singleton()));
                    case ZeldaBinder.Binding.ClassBinding<?> b ->
                        // Class bindings: let Feather resolve the impl,
                        // then store as a singleton supplier
                            suppliers.put(b.type(), new SupplierEntry<>(
                                    () -> null, // resolved lazily via Feather below
                                    true
                            ));
                    default -> {}
                }
            }
        }

        // 4 — Build Feather with class bindings as @Provides modules
        Feather feather = buildFeather(userBinder, instances);

        return new ZeldaInjector(instances, suppliers, feather);
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Returns an instance of the given type.
     * Checks instance registry first, then supplier registry, then Feather.
     */
    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> type) {
        // 1. Instance registry — direct lookup
        Object instance = instanceRegistry.get(type);
        if (instance != null) return type.cast(instance);

        // 2. Supplier registry
        SupplierEntry<?> entry = supplierRegistry.get(type);
        if (entry != null) {
            if (entry.singleton()) {
                return type.cast(singletonCache.computeIfAbsent(type,
                        k -> ((SupplierEntry<T>) entry).supplier().get()));
            }
            return type.cast(((SupplierEntry<T>) entry).supplier().get());
        }

        // 3. Feather — constructor injection
        return feather.instance(type);
    }

    /**
     * Returns a named instance.
     */
    public <T> T get(Class<T> type, String name) {
        return feather.instance(Key.of(type, name));
    }

    /**
     * Injects {@code @Inject}-annotated fields and methods on an existing instance.
     */
    public void injectMembers(Object instance) {
        feather.injectFields(instance);
    }

    // -----------------------------------------------------------------------
    // Feather bootstrap — only handles ClassBindings
    // -----------------------------------------------------------------------

    /**
     * Builds a Feather instance. We use Feather purely for its constructor/field/method
     * injection capability. Class bindings are passed as small provider objects.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Feather buildFeather(ZeldaBinder userBinder,
                                        Map<Class<?>, Object> instances) {
        if (userBinder == null) return Feather.with();

        // Build one anonymous provider object per ClassBinding
        java.util.List<Object> providers = new java.util.ArrayList<>();

        for (ZeldaBinder.Binding<?> binding : userBinder.getBindings()) {
            if (binding instanceof ZeldaBinder.Binding.ClassBinding<?> cb) {
                providers.add(new ClassBindingProvider(cb.type(), cb.impl()));
            }
        }

        return providers.isEmpty() ? Feather.with() : Feather.with(providers.toArray());
    }

    /**
     * A Feather-compatible provider for a single class binding.
     * Feather reads the {@code @Provides} method via reflection.
     */
    private static final class ClassBindingProvider<T> {
        private final Class<T>            iface;
        private final Class<? extends T>  impl;

        ClassBindingProvider(Class<T> iface, Class<? extends T> impl) {
            this.iface = iface;
            this.impl  = impl;
        }

        @org.codejargon.feather.Provides
        @javax.inject.Singleton
        public T provide(Feather feather) {
            return feather.instance(impl);
        }
    }
}