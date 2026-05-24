# zelda-builder

The entry point for the Zelda library. Provides the `Zelda.builder()` fluent API that wires all modules together, initialises `ZeldaContext`, and boots everything in the correct order.

This is the only artifact most plugins need to declare as a dependency — it pulls in everything transitively.

---

## Full builder reference

```java
Zelda.builder()

    // Optional — shared config dir across all plugins on this server
    .centralConfig(Path.of("plugins/Zelda"))

    // Database
    .withDatabase()                          // reads central/database.json
    .withDatabase(customPath)                // explicit path

    // Configuration
    .withConfiguration()                     // uses plugin data folder
    .withConfiguration(customPath)           // explicit base dir

    // Messaging
    .withMessaging()                         // reads central/palette.yml + messages.yml
    .withMessaging(palettePath, messagesPath) // explicit paths

    // UI (Paper/server only)
    .withUI()

    // Outbox
    .withOutbox()                            // 5s poll, batch 50, 3 attempts
    .withOutbox(10, 100, 5)                  // custom settings

    // Injection
    .withInjection()                         // no user bindings
    .withInjection(binder -> binder          // with user bindings
        .bind(EconomyService.class, EconomyServiceImpl.class)
        .bindInstance(Config.class, myConfig)
    )

    // Custom Gson adapters (registered before any module boots)
    .withGsonAdapter(MyClass.class, new MyAdapter())
    .withGsonHierarchyAdapter(Animal.class, new AnimalAdapter())

    // Boot
    .initialize(new PaperPluginAdapter(this));
```

---

## Boot order

When `initialize()` is called:

```
1. ZeldaGson.initialize()      — Gson built, all reflection runs once
2. ZeldaContext.init()          — context available globally
3. ModuleRegistry.enableAll()   — each module.onEnable() in registration order
4. LifecycleHook.afterAllEnabled() — fired on modules that implement it
```

Modules are disabled in **reverse** registration order on `Zelda.shutdown()`.

---

## Accessing modules after init

```java
// Via registry
DatabaseModule db = Zelda.modules().find(DatabaseModule.class).orElseThrow();

// Shortcut
Zelda.modules().find(MessagingModule.class).ifPresent(msg -> {
    msg.send(target, "<primary>Hello!</primary>");
});

// Via injector
ZeldaInjector injector = (ZeldaInjector) ZeldaContext.get().getInjector();
EconomyService economy = injector.get(EconomyService.class);
```

---

## Shutdown

```java
// In onDisable() / ProxyShutdownEvent
Zelda.shutdown();
```

This disables all modules in reverse order and resets `ZeldaContext` and `ZeldaGson`. Safe to call even if `initialize()` was never called.

---

## Optional dependencies

All feature modules are declared `optional` in the builder's POM. You only need to add the ones you use to your plugin's `pom.xml` — the rest are not bundled.

```xml
<!-- Always needed -->
<dependency>
    <groupId>net.kgomc.zelda</groupId>
    <artifactId>zelda-builder</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- Add what you use -->
<dependency>
    <groupId>net.kgomc.zelda</groupId>
    <artifactId>zelda-database</artifactId>
    <version>1.0.0</version>
</dependency>
```