package net.kgomc.zelda.injection.binder;

import org.codejargon.feather.Provides;

import javax.inject.Named;
import javax.inject.Singleton;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Supplier;

/**
 * Fluent binding DSL used at builder time to declare how types are resolved.
 *
 * <p>Bindings declared here are <em>deferred</em> — no instantiation happens
 * until {@link net.kgomc.zelda.injection.injector.ZeldaInjector} is built
 * after all modules are enabled.</p>
 *
 * <h2>Usage in ZeldaBuilder</h2>
 * <pre>{@code
 * Zelda.builder()
 *     .withInjection(binder -> binder
 *         .bind(EconomyService.class, EconomyServiceImpl.class)
 *         .bindInstance(Config.class, myConfig)
 *         .bindSupplier(DataSource.class, () -> pool.getDataSource())
 *     )
 *     .initialize(adapter);
 * }</pre>
 */
public final class ZeldaBinder {

    /**
     * Internal binding entry — sealed to keep variants explicit.
     */
    public sealed interface Binding<T> permits
            Binding.ClassBinding,
            Binding.InstanceBinding,
            Binding.SupplierBinding {

        /** Bind an interface to an implementation class. */
        record ClassBinding<T>(Class<T> type, Class<? extends T> impl)
                implements Binding<T> {}

        /** Bind a type to a pre-existing instance. Always singleton-scoped. */
        record InstanceBinding<T>(Class<T> type, T instance)
                implements Binding<T> {}

        /** Bind a type to a supplier factory. */
        record SupplierBinding<T>(Class<T> type, Supplier<T> supplier, boolean singleton)
                implements Binding<T> {}
    }

    private final List<Binding<?>> bindings = new ArrayList<>();

    // -----------------------------------------------------------------------
    // Fluent API
    // -----------------------------------------------------------------------

    /**
     * Binds an interface to an implementation class.
     * The impl is instantiated by the injector on first request (or once if {@code @Singleton}).
     */
    public <T> ZeldaBinder bind(Class<T> type, Class<? extends T> impl) {
        bindings.add(new Binding.ClassBinding<>(type, impl));
        return this;
    }

    /**
     * Binds a type to a pre-existing instance.
     * Always singleton-scoped — the same instance is returned on every request.
     */
    public <T> ZeldaBinder bindInstance(Class<T> type, T instance) {
        bindings.add(new Binding.InstanceBinding<>(type, instance));
        return this;
    }

    /**
     * Binds a type to a supplier factory.
     *
     * @param singleton if true, the supplier is called once and the result cached
     */
    public <T> ZeldaBinder bindSupplier(Class<T> type, Supplier<T> supplier, boolean singleton) {
        bindings.add(new Binding.SupplierBinding<>(type, supplier, singleton));
        return this;
    }

    /** Convenience — singleton supplier binding. */
    public <T> ZeldaBinder bindSupplier(Class<T> type, Supplier<T> supplier) {
        return bindSupplier(type, supplier, true);
    }

    // -----------------------------------------------------------------------
    // Internal access
    // -----------------------------------------------------------------------

    public List<Binding<?>> getBindings() {
        return Collections.unmodifiableList(bindings);
    }
}