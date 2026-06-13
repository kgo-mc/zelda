package net.kgomc.zelda.core.utils;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * A thread-safe implementation of a lazily-initialized value. The value is
 * created only once upon the first access and retrieved in a synchronized manner
 * to ensure safe publication in a concurrent environment.
 *
 * @param <T> the type of the value to be lazily initialized
 */
public final class ThreadSafeLazy<T> {

    private volatile Supplier<T> supplier;
    private volatile T value;
    private volatile boolean initialized = false;

    public ThreadSafeLazy(Supplier<T> supplier) {
        this.supplier = Objects.requireNonNull(supplier);
    }

    /**
     * C#-style: Lazy.Value
     */
    public T getValue() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    value = supplier.get();
                    initialized = true;
                    supplier = null; // allow GC
                }
            }
        }
        return value;
    }

    /**
     * Whether the value has been created
     */
    public boolean isValueCreated() {
        return initialized;
    }

    /**
     * Optional: reset lazy (recompute on next access)
     */
    public void reset(Supplier<T> newSupplier) {
        synchronized (this) {
            this.supplier = Objects.requireNonNull(newSupplier);
            this.value = null;
            this.initialized = false;
        }
    }

    /**
     * Convenience alias (C# style)
     */
    public T value() {
        return getValue();
    }
}