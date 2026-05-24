package net.kgomc.zelda.injection.module;

import net.kgomc.zelda.core.context.ZeldaContext;
import net.kgomc.zelda.core.lifecycle.LifecycleHook;
import net.kgomc.zelda.core.module.ZeldaModule;
import net.kgomc.zelda.injection.binder.ZeldaBinder;
import net.kgomc.zelda.injection.injector.ZeldaInjector;

/**
 * Zelda injection module.
 *
 * <p>Registered by {@code ZeldaBuilder.withInjection()} and implements
 * {@link LifecycleHook} so it fires <em>after</em> all other modules have
 * completed their {@code onEnable()} — meaning every Zelda module is fully
 * initialised before the injector is built and auto-bindings are wired.</p>
 *
 * <p>After initialisation, the injector is accessible via:</p>
 * <pre>{@code
 * ZeldaInjector injector = ZeldaContext.get().getInjector();
 *
 * EconomyService economy = injector.get(EconomyService.class);
 * injector.injectMembers(myCommandHandler);
 * }</pre>
 */
public final class InjectionModule implements ZeldaModule, LifecycleHook {

    private final ZeldaBinder userBinder;
    private ZeldaInjector     injector;
    private ZeldaContext      context;

    public InjectionModule(ZeldaBinder userBinder) {
        this.userBinder = userBinder;
    }

    @Override
    public String getName() { return "injection"; }

    @Override
    public void onEnable(ZeldaContext context) {
        this.context = context;
        context.getLogger().info("[Zelda/Injection] Waiting for all modules before wiring...");
    }

    @Override
    public void onDisable() {
        injector = null;
    }

    @Override
    public void afterAllEnabled() {
        injector = ZeldaInjector.build(userBinder, context);
        context.getLogger().info("[Zelda/Injection] Injector ready — "
                + context.getRegistry().getAll().size() + " module bindings registered.");
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Returns the built injector.
     *
     * @throws IllegalStateException if called before {@code afterAllEnabled()} fires
     */
    public ZeldaInjector getInjector() {
        if (injector == null) {
            throw new IllegalStateException(
                    "ZeldaInjector is not yet built. " +
                            "Do not call getInjector() before all modules are enabled."
            );
        }
        return injector;
    }
}