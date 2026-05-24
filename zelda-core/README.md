# zelda-core

The platform-agnostic shared kernel. Every other Zelda module depends on this. Contains no platform imports — compiles and runs on Paper, Velocity, and in unit tests without a server.

---

## What's in here

### `ZeldaPlugin` — platform abstraction

The single interface that abstracts over `JavaPlugin` (Paper) and Velocity's plugin system. Provides:

- `getName()` — plugin name
- `getLogger()` — `java.util.logging.Logger`
- `getDataFolder()` — `Path` to the plugin's data directory
- `getRuntimeKind()` — `RuntimeKind.SERVER` or `RuntimeKind.PROXY`
- `runSync(Runnable)` — run on the server main thread
- `runTaskTimer(Runnable, delayTicks, periodTicks)` — repeating scheduled task → `ZeldaTask`

### `ZeldaContext` — process-wide singleton

Holds the active `ZeldaPlugin` and `ModuleRegistry`. Initialised once by `ZeldaBuilder`.

```java
ZeldaContext.get().getPlugin();       // the adapter
ZeldaContext.get().getRegistry();     // all registered modules
ZeldaContext.get().getLogger();       // shortcut
ZeldaContext.get().getDataFolder();   // shortcut
ZeldaContext.get().getInjector();     // ZeldaInjector (if withInjection() was used)
```

### `ZeldaModule` + `ModuleRegistry`

The module contract. Every feature module implements `ZeldaModule`:

```java
public interface ZeldaModule {
    String getName();
    void onEnable(ZeldaContext context);
    void onDisable();
}
```

Modules are registered in insertion order. Disable order is automatically reversed.

### `LifecycleHook`

Optional interface for modules that need to react after all modules are enabled:

```java
public interface LifecycleHook {
    default void afterAllEnabled() {}
    default void beforeDisable() {}
}
```

### `ZeldaSchedulers` — RxJava schedulers

```java
ZeldaSchedulers.io()            // virtual threads — DB, file, network
ZeldaSchedulers.computation()   // CPU-bound work
ZeldaSchedulers.serverThread()  // Paper main thread (via ZeldaPlugin.runSync)
ZeldaSchedulers.newVirtualThread() // one virtual thread per subscription
```

### `ZeldaGson` — shared Gson instance

Built once at startup by `ZeldaBuilder`. Includes adapters for `UUID`, and on Paper: `Location`, `ItemStack`, `Vector`.

```java
Gson gson = ZeldaGson.get();
String json = ZeldaGson.get().toJson(myObject);
MyObject obj = ZeldaGson.get().fromJson(json, MyObject.class);
```

Custom adapters are registered at build time:
```java
Zelda.builder()
    .withGsonAdapter(MyClass.class, new MyClassAdapter())
    .withGsonHierarchyAdapter(Animal.class, new AnimalAdapter())
    .initialize(adapter);
```

---

## Dependencies

- `io.reactivex.rxjava3:rxjava`
- `com.google.code.gson:gson`